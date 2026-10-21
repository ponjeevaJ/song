# Isai World — இசை உலகம்

**பழைய பாடல்களின் நினைவுகள், புதிய பாடல்களின் உற்சாகம்!**  
Old song memories, new song energy.

Isai World is a responsive React/Tailwind music-discovery app and Spring Boot/PostgreSQL API. It includes account registration and JWT login, favorites, playlists, listening history, catalog search, provider-backed audio and YouTube embedded playback, admin-only catalog refresh, daily scheduling, sync logs, and a Tamil/English interface switch.

## Important: licensed catalog and playback

The project does **not** bundle copyrighted songs, artwork, lyrics, or fabricated releases. To enable songs and actual audio, configure `CATALOG_API_URL` to an endpoint you are authorized to use. The endpoint must return the provider-neutral JSON contract below. Its `playbackUrl` must be a genuine, provider-authorized stream URL; Isai World never synthesizes audio or substitutes a demo stream. Omit playback and lyrics URLs when the license does not permit them. The playback controls work with the real stream URL returned by the configured provider.

The generic licensed-provider integration above is available, and an optional YouTube Data API integration is included. YouTube search results are video listings, not independently verified song releases or licensing metadata. Playback stays inside YouTube's official embedded player; Isai World does not extract or proxy audio. YouTube results show the channel name and upload date rather than claiming verified singer, composer, or release information.

To enable YouTube, create a Google Cloud project, enable **YouTube Data API v3**, create an API key restricted to that API (and to your backend's IP where possible), and set `YOUTUBE_API_KEY` in the backend environment or private `.env`. Never put the key in frontend settings or commit it. Restart the backend; the daily scheduler will import recent embeddable Tamil video search results, or an admin can use **Sync catalog now**. Search uses provider quota, so avoid frequent manual syncs. This source can return unrelated or non-music videos and does not guarantee a complete Tamil movie catalog. YouTube API metadata is periodically refreshed and stale YouTube entries are removed; favorites/history for removed entries may also be removed.

Until the API key is configured, empty catalog sections provide a link to search the equivalent topic directly on YouTube. Those links leave Isai World; they do not import or claim to curate the results.

For the licensed-provider endpoint, obtain a provider account and configure an authorized adapter that produces the response below. Provider credentials and playback entitlements remain subject to the provider's terms. No credentials or provider account are included.

```json
{
  "songs": [
    {
      "id": "provider-stable-song-id",
      "title": "Licensed catalog title",
      "movie": "Licensed movie title",
      "singer": "Licensed artist name",
      "composer": "Licensed composer name",
      "album": "Licensed album title",
      "genre": "Melody",
      "releaseDate": "2026-10-20",
      "artworkUrl": "https://provider.example/artwork",
      "playbackUrl": "https://provider.example/authorized-stream",
      "lyricsUrl": "https://provider.example/authorized-lyrics"
    }
  ]
}
```

`id`, `title`, and `songs` are required. Other fields may be omitted or `null`. Dates use ISO `YYYY-MM-DD`. Provider and media URLs must use HTTPS, except localhost development URLs. The API sends `CATALOG_API_TOKEN` as an Authorization Bearer token if provided. Songs are upserted by provider host and stable provider ID; repeat syncs therefore do not create duplicates. Daily refresh runs in `Asia/Kolkata` at the configured six-field cron time. “Today's Tamil Beats” is refreshed from today's releases, falling back to the newest catalog entries when the provider returns no releases for today. “New Tamil Songs Today” only applies a NEW badge to songs with today's release date.

## Run locally with Docker

1. Copy `.env.example` to `.env`.
2. Set strong, unique `POSTGRES_PASSWORD`, `JWT_SECRET` (at least 32 characters), `ADMIN_EMAIL`, and `ADMIN_PASSWORD` (at least 10 characters). Optionally add `YOUTUBE_API_KEY` or your authorized `CATALOG_API_URL` and provider token.
3. Start the app:

   ```powershell
   docker compose --env-file .env up --build
   ```

4. Open `http://localhost:5173`. The API is at `http://localhost:8080`.

The initial admin is inserted only if the configured email does not already exist. Rotate/remove bootstrap credentials after first setup. PostgreSQL data is persisted in the `isaiworld-data` volume.

### Deploying frontend and API on separate hosts

The recommended deployment serves both through one HTTPS origin: the included Nginx configuration proxies `/api/` to Spring Boot without forwarding the browser `Origin` header, so `VITE_API_BASE_URL` stays empty and the backend does not mistake a same-origin request for a cross-origin request. If your frontend and backend use separate hosts, set `VITE_API_BASE_URL` to the backend origin only (for example, `https://api.example.com`, without `/api`) **when building the frontend**, then redeploy it. Set backend `CORS_ALLOWED_ORIGINS` to the exact frontend origin (for example, `https://music.example.com`, with no trailing slash), and restart the backend. Ensure the backend host routes `/api/auth/login` and `/api/auth/register` to Spring Boot; these endpoints are public. A 403 from either endpoint usually means the hosting proxy, WAF, or API base URL is blocking/misrouting the request, not that the account needs admin access. Verify that an invalid login returns 401 from the deployed API before testing real credentials.

Docker Compose passes `VITE_API_BASE_URL` into the frontend image build. Changing it requires rebuilding the frontend image (`docker compose --env-file .env up --build`).

## Run from VS Code on Windows

Use the project's Vite address, `http://localhost:5173/` (or the displayed port if Vite selected another one). Do not open `index.html` using `file://` or VS Code Live Server: those do not proxy `/api`, which can make registration and login fail.

With Node.js, PostgreSQL 17, and the backend JAR already built with Maven, run both VS Code tasks from **Terminal → Run Task**:

- `Isai World — Backend + Local Database`: creates/starts a separate Isai World PostgreSQL database on `127.0.0.1:5433`, then starts the API on port `8080`. It does not access or change other PostgreSQL databases.
- `Isai World — Live Server`: starts Vite on port `5173` and proxies API requests to `127.0.0.1:8080`.

The local development database uses a randomly generated, process-local JWT signing key and local trust authentication; it is for development only and must not be exposed to a network. Set `ISAI_POSTGRES_BIN` if your PostgreSQL tools are installed outside `C:\Program Files\PostgreSQL\17\bin`.

To use YouTube locally, copy `.env.example` to `.env`, set `YOUTUBE_API_KEY` there, and restart the backend task. The Windows startup script reads this setting without printing it. Do not commit the key or enter it into the frontend.

When accessing the Vite app from another device on your LAN, use the host machine's LAN URL (for example, `http://192.168.1.3:5173/`). The Vite API proxy removes the browser `Origin` header because `/api` is same-origin through that proxy; otherwise Spring CORS would reject LAN IP origins that are not in the local CORS allowlist.

## Run frontend and backend separately

Requires Node.js 20+ and Java 21 with Maven.

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

In another PowerShell terminal, set the backend environment variables from `.env`, then:

```powershell
Set-Location backend
mvn spring-boot:run
```

The Vite development server proxies `/api` to `http://localhost:8080`. The backend expects PostgreSQL at `localhost:5432` by default.

## Deploy a public portal on Render

The root `render.yaml` defines the frontend, API, and private PostgreSQL database in Singapore. The frontend serves the app over HTTPS and proxies `/api` to the API, so both use one origin. The blueprint uses persistent paid plans; Render shows the resource costs and asks you to approve them before creating services. No Render resources are created just by adding this file.

1. Push this project to a **private GitHub repository**. Do not upload `.env`, provider credentials, or generated build files.
2. In Render, choose **New → Blueprint**, connect the repository, and select `render.yaml`.
3. Review the plans and costs before approving resource creation. Enter the admin email and password in Render's environment-variable prompts. Add provider credentials there only if you have an authorized catalog provider; without one, the app will deploy but its song catalog stays empty.
4. Deploy the blueprint. Open the public URL shown for the `isai-world` web service. A custom domain is optional.

Keep `JWT_SECRET` generated by Render, and keep database and provider credentials in Render's environment settings, never in Git. The database is not exposed to public IP addresses.

## API outline

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register a listener |
| POST | `/api/auth/login` | Public | Obtain a signed JWT |
| GET | `/api/songs` | Public | Search/filter songs (`query`, `year`, `genre`) |
| GET | `/api/songs/today` | Public | Today's releases or latest catalog fallback |
| GET | `/api/playlists/today` | Public | Today's Tamil Beats |
| GET | `/api/catalog/status` | Public | Provider configuration and last-sync summary |
| GET/POST/DELETE | `/api/me/favorites[/songId]` | JWT | Manage favorites |
| GET/POST | `/api/me/playlists` | JWT | List/create personal playlists |
| GET/POST/DELETE | `/api/me/playlists/{id}/songs[/songId]` | JWT | Read and manage playlist tracks |
| GET/POST | `/api/me/history[/songId]` | JWT | Read recent plays and record a play |
| GET/POST/DELETE | `/api/me/follows[/artistId]` | JWT | Follow artists |
| POST | `/api/admin/sync` | Admin JWT | Trigger an immediate provider sync |
| GET | `/api/admin/sync-logs` | Admin JWT | Inspect recent refresh outcomes/errors |

## Data and security

`backend/src/main/resources/schema.sql` creates user, song, artist, album, movie, genre, playlist, favorites, artist-follow, listening-history, and synchronization-log tables. Passwords are BCrypt-hashed; JWTs are signed and time-limited; account/library and admin APIs require authentication/roles; SQL values are parameterized. Keep `.env` out of source control and restrict the admin account and provider token. Configure `CORS_ALLOWED_ORIGINS` for the deployed frontend origin. Use HTTPS for every deployed service.

The app intentionally starts with an empty song catalog. No songs/audio are seeded, and daily jobs do not invent releases. Do not scrape or copy songs from arbitrary websites. A catalog remains empty until an authorized music provider and its credentials are configured. Providers may restrict artwork, streaming, preview, lyric, or caching use independently; import and play only media your account is licensed to use.
