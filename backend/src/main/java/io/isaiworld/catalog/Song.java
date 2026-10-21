package io.isaiworld.catalog;

import java.time.LocalDate;

public record Song(String id, String title, String movie, String singer, String composer,
                   String album, String genre, LocalDate releaseDate, String artworkUrl,
                   String playbackUrl, String lyricsUrl) {}
