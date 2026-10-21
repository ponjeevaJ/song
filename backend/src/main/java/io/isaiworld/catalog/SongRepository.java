package io.isaiworld.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SongRepository {
    private static final String SELECT = """
        SELECT id, title, movie_name AS movie, singer, composer, album_name AS album,
               genre_name AS genre, release_date, artwork_url, playback_url, lyrics_url
        FROM songs
        """;
    private static final RowMapper<Song> MAPPER = (rs, row) -> new Song(
        rs.getString("id"), rs.getString("title"), rs.getString("movie"), rs.getString("singer"),
        rs.getString("composer"), rs.getString("album"), rs.getString("genre"),
        rs.getDate("release_date") == null ? null : rs.getDate("release_date").toLocalDate(),
        rs.getString("artwork_url"), rs.getString("playback_url"), rs.getString("lyrics_url"));
    private final JdbcTemplate jdbc;

    public SongRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Song> search(String query, Integer year, String genre) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1=1");
        var args = new java.util.ArrayList<Object>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (title ILIKE ? OR movie_name ILIKE ? OR singer ILIKE ? OR composer ILIKE ? OR album_name ILIKE ? OR genre_name ILIKE ?)");
            String pattern = "%" + query.trim() + "%";
            args.addAll(java.util.Collections.nCopies(6, pattern));
        }
        if (year != null) {
            sql.append(" AND EXTRACT(YEAR FROM release_date) = ?");
            args.add(year);
        }
        if (genre != null && !genre.isBlank()) {
            sql.append(" AND genre_name ILIKE ?");
            args.add("%" + genre.trim() + "%");
        }
        sql.append(" ORDER BY release_date DESC NULLS LAST, title LIMIT 500");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<Song> todayOrLatest() {
        List<Song> today = jdbc.query(
            SELECT + " WHERE release_date = (now() AT TIME ZONE 'Asia/Kolkata')::date ORDER BY title LIMIT 100", MAPPER);
        return today.isEmpty()
            ? jdbc.query(SELECT + " ORDER BY release_date DESC NULLS LAST, title LIMIT 100", MAPPER)
            : today;
    }

    public List<Song> trending() {
        Integer plays = jdbc.queryForObject("SELECT count(*) FROM listening_history", Integer.class);
        if (plays == 0) return search(null, null, null);
        return jdbc.query("""
            SELECT s.id, s.title, s.movie_name AS movie, s.singer, s.composer, s.album_name AS album,
                   s.genre_name AS genre, s.release_date, s.artwork_url, s.playback_url, s.lyrics_url
            FROM songs s LEFT JOIN listening_history h ON h.song_id = s.id
            GROUP BY s.id
            ORDER BY count(h.id) DESC, s.release_date DESC NULLS LAST, s.title
            LIMIT 100
            """, MAPPER);
    }

    public Optional<Song> findById(String id) {
        return jdbc.query(SELECT + " WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public void requireSong(String id) {
        if (findById(id).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Song was not found in the catalog.");
        }
    }
}
