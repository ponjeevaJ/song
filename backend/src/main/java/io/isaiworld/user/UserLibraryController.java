package io.isaiworld.user;

import io.isaiworld.catalog.Song;
import io.isaiworld.catalog.SongRepository;
import io.isaiworld.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class UserLibraryController {
    private final JdbcTemplate jdbc;
    private final SongRepository songs;

    public UserLibraryController(JdbcTemplate jdbc, SongRepository songs) {
        this.jdbc = jdbc;
        this.songs = songs;
    }

    @GetMapping("/favorites")
    public List<Song> favorites(@AuthenticationPrincipal AccountPrincipal user) {
        return jdbc.query("""
            SELECT s.id, s.title, s.movie_name AS movie, s.singer, s.composer, s.album_name AS album,
                   s.genre_name AS genre, s.release_date, s.artwork_url, s.playback_url, s.lyrics_url
            FROM favorites f JOIN songs s ON s.id = f.song_id
            WHERE f.user_id = ? ORDER BY f.created_at DESC
            """, (rs, row) -> new Song(rs.getString("id"), rs.getString("title"), rs.getString("movie"),
                rs.getString("singer"), rs.getString("composer"), rs.getString("album"), rs.getString("genre"),
                rs.getDate("release_date") == null ? null : rs.getDate("release_date").toLocalDate(),
                rs.getString("artwork_url"), rs.getString("playback_url"), rs.getString("lyrics_url"), null), user.id());
    }

    @PostMapping("/favorites/{songId}")
    public void addFavorite(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String songId) {
        songs.requireSong(songId);
        jdbc.update("INSERT INTO favorites(user_id, song_id) VALUES (?, ?) ON CONFLICT DO NOTHING", user.id(), songId);
    }

    @DeleteMapping("/favorites/{songId}")
    public void removeFavorite(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String songId) {
        jdbc.update("DELETE FROM favorites WHERE user_id = ? AND song_id = ?", user.id(), songId);
    }

    @PostMapping("/history/{songId}")
    public void recordPlay(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String songId) {
        songs.requireSong(songId);
        jdbc.update("INSERT INTO listening_history(user_id, song_id) VALUES (?, ?)", user.id(), songId);
    }

    @GetMapping("/history")
    public List<Song> history(@AuthenticationPrincipal AccountPrincipal user) {
        return jdbc.query("""
            SELECT s.id, s.title, s.movie_name AS movie, s.singer, s.composer, s.album_name AS album,
                   s.genre_name AS genre, s.release_date, s.artwork_url, s.playback_url, s.lyrics_url
            FROM listening_history h JOIN songs s ON s.id = h.song_id
            WHERE h.user_id = ? ORDER BY h.played_at DESC LIMIT 50
            """, (rs, row) -> new Song(rs.getString("id"), rs.getString("title"), rs.getString("movie"),
                rs.getString("singer"), rs.getString("composer"), rs.getString("album"), rs.getString("genre"),
                rs.getDate("release_date") == null ? null : rs.getDate("release_date").toLocalDate(),
                rs.getString("artwork_url"), rs.getString("playback_url"), rs.getString("lyrics_url"), null), user.id());
    }

    @GetMapping("/playlists")
    public List<Map<String, Object>> playlists(@AuthenticationPrincipal AccountPrincipal user) {
        return jdbc.queryForList("""
            SELECT p.id, p.name, p.description, p.updated_at,
                   COUNT(ps.song_id) AS song_count
            FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlist_id = p.id
            WHERE p.user_id = ? GROUP BY p.id ORDER BY p.updated_at DESC
            """, user.id());
    }

    @PostMapping("/playlists")
    public Map<String, Object> createPlaylist(@AuthenticationPrincipal AccountPrincipal user,
                                               @Valid @RequestBody PlaylistRequest request) {
        try {
            return jdbc.queryForMap("""
                INSERT INTO playlists(user_id, name, description)
                VALUES (?, ?, ?) RETURNING id, name, description, updated_at
                """, user.id(), request.name().trim(), request.description());
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A playlist with this name already exists.");
        }
    }

    @GetMapping("/playlists/{playlistId}/songs")
    public List<Song> playlistSongs(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long playlistId) {
        requirePlaylistOwner(user.id(), playlistId);
        return jdbc.query("""
            SELECT s.id, s.title, s.movie_name AS movie, s.singer, s.composer, s.album_name AS album,
                   s.genre_name AS genre, s.release_date, s.artwork_url, s.playback_url, s.lyrics_url
            FROM playlist_songs ps JOIN songs s ON s.id = ps.song_id
            WHERE ps.playlist_id = ? ORDER BY ps.position
            """, (rs, row) -> new Song(rs.getString("id"), rs.getString("title"), rs.getString("movie"),
                rs.getString("singer"), rs.getString("composer"), rs.getString("album"), rs.getString("genre"),
                rs.getDate("release_date") == null ? null : rs.getDate("release_date").toLocalDate(),
                rs.getString("artwork_url"), rs.getString("playback_url"), rs.getString("lyrics_url"), null), playlistId);
    }

    @PostMapping("/playlists/{playlistId}/songs/{songId}")
    public void addPlaylistSong(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long playlistId,
                                @PathVariable String songId) {
        requirePlaylistOwner(user.id(), playlistId);
        songs.requireSong(songId);
        Integer nextPosition = jdbc.queryForObject(
            "SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_songs WHERE playlist_id = ?", Integer.class, playlistId);
        jdbc.update("""
            INSERT INTO playlist_songs(playlist_id, song_id, position) VALUES (?, ?, ?)
            ON CONFLICT (playlist_id, song_id) DO NOTHING
            """, playlistId, songId, nextPosition);
        jdbc.update("UPDATE playlists SET updated_at = now() WHERE id = ?", playlistId);
    }

    @DeleteMapping("/playlists/{playlistId}/songs/{songId}")
    public void removePlaylistSong(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long playlistId,
                                   @PathVariable String songId) {
        requirePlaylistOwner(user.id(), playlistId);
        jdbc.update("DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?", playlistId, songId);
        jdbc.update("UPDATE playlists SET updated_at = now() WHERE id = ?", playlistId);
    }

    @GetMapping("/follows")
    public List<Map<String, Object>> follows(@AuthenticationPrincipal AccountPrincipal user) {
        return jdbc.queryForList("""
            SELECT a.id, a.name, a.image_url FROM artist_follows f JOIN artists a ON a.id = f.artist_id
            WHERE f.user_id = ? ORDER BY a.name
            """, user.id());
    }

    @PostMapping("/follows/{artistId}")
    public void followArtist(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long artistId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM artists WHERE id = ?", Integer.class, artistId);
        if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist was not found.");
        jdbc.update("INSERT INTO artist_follows(user_id, artist_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            user.id(), artistId);
    }

    @DeleteMapping("/follows/{artistId}")
    public void unfollowArtist(@AuthenticationPrincipal AccountPrincipal user, @PathVariable long artistId) {
        jdbc.update("DELETE FROM artist_follows WHERE user_id = ? AND artist_id = ?", user.id(), artistId);
    }

    private void requirePlaylistOwner(long userId, long playlistId) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM playlists WHERE id = ? AND user_id = ? AND is_daily = false",
            Integer.class, playlistId, userId);
        if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist was not found.");
    }

    public record PlaylistRequest(@NotBlank @Size(max = 150) String name, @Size(max = 500) String description) {}
}
