package io.isaiworld.catalog;

import io.isaiworld.catalog.CatalogWriter.ProviderSong;
import io.isaiworld.catalog.CatalogWriter.SyncCounts;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);
    private final String apiUrl;
    private final String apiToken;
    private final YouTubeCatalogClient youTubeCatalog;
    private final RestClient restClient;
    private final JdbcTemplate jdbc;
    private final CatalogWriter writer;

    public CatalogSyncService(@Value("${isai.catalog.url}") String apiUrl,
                              @Value("${isai.catalog.token}") String apiToken,
                              YouTubeCatalogClient youTubeCatalog,
                              RestClient.Builder restClientBuilder,
                              JdbcTemplate jdbc, CatalogWriter writer) {
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.youTubeCatalog = youTubeCatalog;
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.jdbc = jdbc;
        this.writer = writer;
    }

    @Scheduled(cron = "${isai.catalog.sync-cron}", zone = "Asia/Kolkata")
    public void scheduledSync() {
        purgeExpiredYouTubeEntries();
        if (!configured()) {
            log.info("Scheduled catalog refresh skipped: no catalog provider is configured.");
            return;
        }
        try {
            synchronize();
        } catch (RuntimeException exception) {
            log.error("Scheduled catalog refresh failed; see sync_logs for details. {}", safeError(exception.getMessage()));
        }
    }

    private void purgeExpiredYouTubeEntries() {
        jdbc.update("DELETE FROM songs WHERE provider = 'youtube.com' AND updated_at < now() - interval '30 days'");
    }

    public SyncResult synchronize() {
        if (!configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Configure CATALOG_API_URL or YOUTUBE_API_KEY before refreshing the catalog.");
        }
        long logId = jdbc.queryForObject(
            "INSERT INTO sync_logs(status) VALUES ('RUNNING') RETURNING id", Long.class);
        try {
            List<ProviderSong> providerSongs;
            String provider;
            if (apiUrl != null && !apiUrl.isBlank()) {
                validateProviderUrl(apiUrl);
                var request = restClient.get().uri(URI.create(apiUrl));
                if (!apiToken.isBlank()) request.header("Authorization", "Bearer " + apiToken);
                ProviderResponse response = request.retrieve().body(ProviderResponse.class);
                if (response == null || response.songs() == null) {
                    throw new IllegalArgumentException("Catalog response must contain a songs array.");
                }
                providerSongs = response.songs();
                provider = providerName();
            } else {
                providerSongs = youTubeCatalog.searchTamilMusic();
                provider = "youtube.com";
            }
            validateSongs(providerSongs);
            SyncCounts counts = writer.save(provider, providerSongs);
            if ("youtube.com".equals(provider)) purgeExpiredYouTubeEntries();
            jdbc.update("""
                UPDATE sync_logs SET status = 'SUCCESS', completed_at = now(), songs_added = ?, songs_updated = ?
                WHERE id = ?
                """, counts.added(), counts.updated(), logId);
            return new SyncResult(counts.added(), counts.updated(), providerSongs.size());
        } catch (RestClientException | IllegalArgumentException | DataAccessException exception) {
            jdbc.update("UPDATE sync_logs SET status = 'FAILED', completed_at = now(), error_message = ? WHERE id = ?",
                safeError(exception.getMessage()), logId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Catalog synchronization failed. Check the sync status and provider configuration.", exception);
        }
    }

    public CatalogStatus status() {
        var lastSync = jdbc.query("""
            SELECT status, started_at, completed_at, songs_added, songs_updated, error_message
            FROM sync_logs ORDER BY id DESC LIMIT 1
            """, (rs, row) -> new SyncLog(rs.getString("status"), rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("completed_at", java.time.OffsetDateTime.class), rs.getInt("songs_added"),
                rs.getInt("songs_updated"), rs.getString("error_message")));
        var latestSuccess = jdbc.query("""
            SELECT completed_at FROM sync_logs WHERE status = 'SUCCESS' ORDER BY id DESC LIMIT 1
            """, (rs, row) -> rs.getObject(1, java.time.OffsetDateTime.class));
        SyncLog logEntry = lastSync.isEmpty() ? null : lastSync.getFirst();
        SyncStatus safeLog = logEntry == null ? null : new SyncStatus(logEntry.status(), logEntry.startedAt(),
            logEntry.completedAt(), logEntry.songsAdded(), logEntry.songsUpdated(),
            "FAILED".equals(logEntry.status()) ? "Catalog refresh failed. Administrators can inspect the sync logs." : null);
        return new CatalogStatus(configured(), latestSuccess.isEmpty() ? null : latestSuccess.getFirst(), safeLog);
    }

    public List<SyncLog> logs() {
        return jdbc.query("""
            SELECT status, started_at, completed_at, songs_added, songs_updated, error_message
            FROM sync_logs ORDER BY id DESC LIMIT 30
            """, (rs, row) -> new SyncLog(rs.getString("status"),
                rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("completed_at", java.time.OffsetDateTime.class), rs.getInt("songs_added"),
                rs.getInt("songs_updated"), rs.getString("error_message")));
    }

    private boolean configured() {
        return (apiUrl != null && !apiUrl.isBlank()) || youTubeCatalog.configured();
    }

    private String providerName() {
        String host = URI.create(apiUrl).getHost();
        if (host.length() > 100) {
            throw new IllegalArgumentException("Catalog provider host exceeds the supported length.");
        }
        return host;
    }

    private void validateProviderUrl(String value) {
        URI uri = URI.create(value);
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean secureTransport = "https".equalsIgnoreCase(uri.getScheme())
            || (local && "http".equalsIgnoreCase(uri.getScheme()));
        if (uri.getHost() == null || uri.getUserInfo() != null || !secureTransport) {
            throw new IllegalArgumentException("Catalog provider URL must use HTTPS.");
        }
    }

    private void validateSongs(List<ProviderSong> songs) {
        for (ProviderSong song : songs) {
            if (song == null || song.id() == null || song.id().isBlank()
                || song.title() == null || song.title().isBlank()) {
                throw new IllegalArgumentException("Catalog provider returned a song without a stable id or title.");
            }
            validateMediaUrl(song.artworkUrl(), "artwork");
            validateMediaUrl(song.playbackUrl(), "playback");
            validateMediaUrl(song.lyricsUrl(), "lyrics");
        }
    }

    private void validateMediaUrl(String value, String field) {
        if (value == null || value.isBlank()) return;
        URI uri = URI.create(value);
        boolean local = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean secureTransport = "https".equalsIgnoreCase(uri.getScheme())
            || (local && "http".equalsIgnoreCase(uri.getScheme()));
        if (uri.getHost() == null || uri.getUserInfo() != null || !secureTransport) {
            throw new IllegalArgumentException("Song " + field + " URL must use HTTPS.");
        }
    }

    private String safeError(String message) {
        if (message == null) return "Catalog provider request failed.";
        String scrubbed = (apiToken.isBlank() ? message : message.replace(apiToken, "[redacted]"));
        scrubbed = youTubeCatalog.redactApiKey(scrubbed);
        if (apiUrl != null && !apiUrl.isBlank()) scrubbed = scrubbed.replace(apiUrl, "[provider URL]");
        scrubbed = scrubbed
            .replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[redacted]")
            .replaceAll("(?i)(authorization|token|api[_-]?key)(\\s*[=:]\\s*)[^&\\s,]+", "$1$2[redacted]");
        return scrubbed.length() > 1000 ? scrubbed.substring(0, 1000) : scrubbed;
    }

    public record ProviderResponse(List<ProviderSong> songs) {}
    public record SyncResult(int added, int updated, int received) {}
    public record CatalogStatus(boolean configured, java.time.OffsetDateTime lastSuccessAt, SyncStatus lastSync) {}
    public record SyncStatus(String status, java.time.OffsetDateTime startedAt, java.time.OffsetDateTime completedAt,
                             int songsAdded, int songsUpdated, String errorMessage) {}
    public record SyncLog(String status, java.time.OffsetDateTime startedAt, java.time.OffsetDateTime completedAt,
                          int songsAdded, int songsUpdated, String errorMessage) {}
}
