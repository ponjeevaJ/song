$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $workspace '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*YOUTUBE_API_KEY\s*=\s*(.*?)\s*$') {
            $value = $Matches[1].Trim()
            if ($value.Length -ge 2 -and $value[0] -eq $value[$value.Length - 1] -and $value[0] -in @('"', "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                $env:YOUTUBE_API_KEY = $value
            }
            break
        }
    }
}
$postgresBin = if ($env:ISAI_POSTGRES_BIN) {
    $env:ISAI_POSTGRES_BIN
} else {
    'C:\Program Files\PostgreSQL\17\bin'
}
$postgresData = Join-Path $env:LOCALAPPDATA 'IsaiWorld\postgres-data'
$logs = Join-Path $env:LOCALAPPDATA 'IsaiWorld\logs'
$port = 5433

$pgCtl = Join-Path $postgresBin 'pg_ctl.exe'
$initDb = Join-Path $postgresBin 'initdb.exe'
$createdb = Join-Path $postgresBin 'createdb.exe'
$psql = Join-Path $postgresBin 'psql.exe'
$pgIsReady = Join-Path $postgresBin 'pg_isready.exe'

foreach ($tool in @($pgCtl, $initDb, $createdb, $psql, $pgIsReady)) {
    if (-not (Test-Path $tool)) {
        throw "PostgreSQL command not found: $tool. Install PostgreSQL 17 or set ISAI_POSTGRES_BIN."
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $postgresData), $logs | Out-Null
if (-not (Test-Path (Join-Path $postgresData 'PG_VERSION'))) {
    & $initDb -D $postgresData -U isaiworld --auth=trust --encoding=UTF8 --no-locale
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not initialize the Isai World local database.'
    }
}

& $pgIsReady -h 127.0.0.1 -p $port *> $null
if ($LASTEXITCODE -ne 0) {
    & $pgCtl -D $postgresData -l (Join-Path $logs 'postgres.log') -o "-p $port -h 127.0.0.1" start
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start the Isai World database on 127.0.0.1:$port."
    }
}

$ready = $false
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    & $pgIsReady -h 127.0.0.1 -p $port *> $null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    throw "The Isai World database did not become ready on port $port."
}

$env:PGHOST = '127.0.0.1'
$env:PGPORT = "$port"
$env:PGUSER = 'isaiworld'
$databaseExists = & $psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'isaiworld'"
if ($LASTEXITCODE -ne 0) {
    throw 'Could not check the Isai World database.'
}
if ($databaseExists.Trim() -ne '1') {
    & $createdb isaiworld
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not create the Isai World database.'
    }
}

$jar = Join-Path $workspace 'backend\target\isai-world-api-1.0.0.jar'
$sourceRoot = Join-Path $workspace 'backend\src'
$newestSource = Get-ChildItem $sourceRoot -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$buildRequired = -not (Test-Path $jar)
if (-not $buildRequired -and $newestSource.LastWriteTime -gt (Get-Item $jar).LastWriteTime) {
    $buildRequired = $true
}

if ($buildRequired) {
    $maven = (Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1)
    if (-not $maven) {
        $bundledMaven = Join-Path $env:TEMP 'isai-maven\apache-maven-3.9.11\bin\mvn.cmd'
        if (Test-Path $bundledMaven) {
            $maven = Get-Item $bundledMaven
        }
    }
    if (-not $maven) {
        throw 'Backend sources changed but Maven is not installed. Install Maven 3.9+ and run the backend task again.'
    }
    Push-Location (Join-Path $workspace 'backend')
    try {
        & $maven.Source -q -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw 'The backend build failed. Review the Maven output before starting the app.'
        }
    } finally {
        Pop-Location
    }
}

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$port/isaiworld"
$env:SPRING_DATASOURCE_USERNAME = 'isaiworld'
$env:SPRING_DATASOURCE_PASSWORD = ''
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $secretBytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($secretBytes)
    } finally {
        $rng.Dispose()
    }
    $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
if ([string]::IsNullOrWhiteSpace($env:CORS_ALLOWED_ORIGINS)) {
    $env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
}
if ($null -eq $env:CATALOG_API_URL) {
    $env:CATALOG_API_URL = ''
}
if ($null -eq $env:CATALOG_API_TOKEN) {
    $env:CATALOG_API_TOKEN = ''
}

Write-Output 'Isai World API starting at http://localhost:8080'
Write-Output 'Local Isai World PostgreSQL uses its own data directory on port 5433.'
if ([string]::IsNullOrWhiteSpace($env:CATALOG_API_URL) -and [string]::IsNullOrWhiteSpace($env:YOUTUBE_API_KEY)) {
    Write-Output 'No music provider is configured. Set YOUTUBE_API_KEY or CATALOG_API_URL to enable the catalog.'
}
& java -jar $jar
if ($LASTEXITCODE -ne 0) {
    throw "Isai World API stopped with exit code $LASTEXITCODE."
}
