package io.isaiworld.catalog;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogWriter {
    private final JdbcTemplate jdbc;

    public CatalogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public SyncCounts save(String provider, List<ProviderSong> songs) {
        int added = 0;
        int updated = 0;
        for (ProviderSong song : songs) {
            if (song.id() == null || song.id().isBlank() || song.title() == null || song.title().isBlank()) {
                throw new IllegalArgumentException("Catalog provider returned a song without a stable id or title.");
            }
            String stableId = song.id().trim();
            String id = provider + ":" + stableId;
            if (id.length() > 300) {
                throw new IllegalArgumentException("Catalog provider song id is longer than the supported maximum.");
            }
            boolean exists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM songs WHERE id = ?)", Boolean.class, id);
            Long artistId = lookup("artists", song.singer(), song.artworkUrl(), null, null);
            Long albumId = lookup("albums", song.album(), song.artworkUrl(), song.releaseDate(), null);
            Long movieId = lookup("movies", song.movie(), null, null,
                song.releaseDate() == null ? null : song.releaseDate().getYear());
            Long genreId = lookup("genres", song.genre(), null, null, null);
            jdbc.update("""
                INSERT INTO songs(id, provider, title, movie_name, singer, composer, album_name, genre_name,
                                  release_date, artwork_url, playback_url, lyrics_url, artist_id, album_id, movie_id, genre_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET provider = EXCLUDED.provider, title = EXCLUDED.title,
                    movie_name = EXCLUDED.movie_name, singer = EXCLUDED.singer, composer = EXCLUDED.composer,
                    album_name = EXCLUDED.album_name, genre_name = EXCLUDED.genre_name,
                    release_date = EXCLUDED.release_date, artwork_url = EXCLUDED.artwork_url,
                    playback_url = EXCLUDED.playback_url, lyrics_url = EXCLUDED.lyrics_url,
                    artist_id = EXCLUDED.artist_id, album_id = EXCLUDED.album_id, movie_id = EXCLUDED.movie_id,
                    genre_id = EXCLUDED.genre_id, updated_at = now()
                """, id, provider, song.title().trim(), song.movie(), song.singer(), song.composer(), song.album(),
                song.genre(), song.releaseDate(), song.artworkUrl(), song.playbackUrl(), song.lyricsUrl(),
                artistId, albumId, movieId, genreId);
            if (exists) updated++;
            else added++;
        }
        refreshDailyPlaylist(provider, songs);
        return new SyncCounts(added, updated);
    }

    private Long lookup(String table, String name, String artwork, LocalDate date, Integer year) {
        if (name == null || name.isBlank()) return null;
        return switch (table) {
            case "artists" -> jdbc.queryForObject("""
                INSERT INTO artists(name, image_url) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET image_url = COALESCE(EXCLUDED.image_url, artists.image_url)
                RETURNING id
                """, Long.class, name.trim(), artwork);
            case "albums" -> jdbc.queryForObject("""
                INSERT INTO albums(name, artwork_url, release_date) VALUES (?, ?, ?)
                ON CONFLICT (name) DO UPDATE SET artwork_url = COALESCE(EXCLUDED.artwork_url, albums.artwork_url),
                    release_date = COALESCE(EXCLUDED.release_date, albums.release_date)
                RETURNING id
                """, Long.class, name.trim(), artwork, date);
            case "movies" -> jdbc.queryForObject("""
                INSERT INTO movies(name, release_year) VALUES (?, ?)
                ON CONFLICT (name) DO UPDATE SET release_year = COALESCE(EXCLUDED.release_year, movies.release_year)
                RETURNING id
                """, Long.class, name.trim(), year);
            case "genres" -> jdbc.queryForObject("""
                INSERT INTO genres(name) VALUES (?)
                ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """, Long.class, name.trim());
            default -> throw new IllegalArgumentException("Unsupported catalog lookup.");
        };
    }

    private void refreshDailyPlaylist(String provider, List<ProviderSong> songs) {
        Long playlistId = jdbc.queryForObject("""
            INSERT INTO playlists(user_id, name, description, is_daily, updated_at)
            VALUES (NULL, 'Today''s Tamil Beats', 'Daily refreshed from the connected licensed catalog.', true, now())
            ON CONFLICT (name) WHERE is_daily = true DO UPDATE SET updated_at = EXCLUDED.updated_at
            RETURNING id
            """, Long.class);
        jdbc.update("DELETE FROM playlist_songs WHERE playlist_id = ?", playlistId);
        int position = 0;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        var todaysSongs = songs.stream().filter(song -> today.equals(song.releaseDate())).toList();
        if (!todaysSongs.isEmpty()) {
            for (ProviderSong song : todaysSongs) {
                addDailySong(playlistId, provider + ":" + song.id().trim(), position++);
            }
        } else {
            List<String> latest = jdbc.query(
                "SELECT id FROM songs ORDER BY release_date DESC NULLS LAST, title LIMIT 20",
                (rs, row) -> rs.getString(1));
            for (String songId : latest) addDailySong(playlistId, songId, position++);
        }
    }

    private void addDailySong(long playlistId, String songId, int position) {
        jdbc.update("""
            INSERT INTO playlist_songs(playlist_id, song_id, position) VALUES (?, ?, ?)
            ON CONFLICT (playlist_id, song_id) DO UPDATE SET position = EXCLUDED.position
            """, playlistId, songId, position);
    }

    public record ProviderSong(String id, String title, String movie, String singer, String composer,
                               String album, String genre, LocalDate releaseDate, String artworkUrl,
                               String playbackUrl, String lyricsUrl) {}
    public record SyncCounts(int added, int updated) {}
}
