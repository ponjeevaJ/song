package io.isaiworld.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final SongRepository songs;
    private final CatalogSyncService sync;
    private final AppleMusicSearchService externalSearch;
    private final YouTubeCatalogClient youtubeSearch;

    public CatalogController(SongRepository songs, CatalogSyncService sync, AppleMusicSearchService externalSearch,
                             YouTubeCatalogClient youtubeSearch) {
        this.songs = songs;
        this.sync = sync;
        this.externalSearch = externalSearch;
        this.youtubeSearch = youtubeSearch;
    }

    @GetMapping("/songs")
    public List<Song> search(@RequestParam(required = false) String query,
                             @RequestParam(required = false) Integer year,
                             @RequestParam(required = false) String genre) {
        List<Song> catalogResults = songs.search(query, year, genre);
        if (!catalogResults.isEmpty() || query == null || query.isBlank()) {
            return catalogResults;
        }
        if (youtubeSearch.configured() && year == null && (genre == null || genre.isBlank())) {
            List<Song> youtubeResults = youtubeSearch.searchSongs(query);
            if (!youtubeResults.isEmpty()) return youtubeResults;
        }
        return externalSearch.search(query, year, genre);
    }

    @GetMapping("/songs/today")
    public List<Song> todayOrLatest() {
        return songs.todayOrLatest();
    }

    @GetMapping("/songs/trending")
    public List<Song> trending() {
        return songs.trending();
    }

    @GetMapping("/catalog/status")
    public CatalogSyncService.CatalogStatus status() {
        return sync.status();
    }

    @GetMapping("/playlists/today")
    public Map<String, Object> todaysTamilBeats() {
        return Map.of("name", "Today's Tamil Beats", "songs", songs.todayOrLatest());
    }
}
