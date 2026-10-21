package io.isaiworld.catalog;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AppleMusicSearchService {
    private static final Logger log = LoggerFactory.getLogger(AppleMusicSearchService.class);
    private static final String SEARCH_URL = "https://itunes.apple.com/search";
    private static final int MAX_QUERY_LENGTH = 100;
    private final RestClient restClient;

    public AppleMusicSearchService(RestClient.Builder restClientBuilder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public List<Song> search(String query, Integer year, String genre) {
        String term = query.trim();
        if (term.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 100 characters or fewer.");
        }

        SearchResponse response;
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                .queryParam("term", "{term}")
                .queryParam("entity", "song")
                .queryParam("country", "IN")
                .queryParam("limit", 25)
                .encode()
                .buildAndExpand(term)
                .toUri();
            response = restClient.get().uri(uri).retrieve().body(SearchResponse.class);
        } catch (RestClientException exception) {
            log.warn("Apple music search failed ({})", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Online music search is temporarily unavailable. Please try again.", exception);
        }

        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
            .filter(track -> track != null && track.trackId() != null && track.trackName() != null
                && !track.trackName().isBlank() && isApplePreview(track.previewUrl())
                && isAppleArtwork(track.artworkUrl100()) && isAppleTrackPage(track.trackViewUrl()))
            .map(this::toSong)
            .filter(song -> year == null || (song.releaseDate() != null && song.releaseDate().getYear() == year))
            .filter(song -> genre == null || genre.isBlank()
                || (song.genre() != null && song.genre().toLowerCase(Locale.ROOT).contains(genre.toLowerCase(Locale.ROOT))))
            .toList();
    }

    private Song toSong(AppleTrack track) {
        var releaseDate = track.releaseDate() == null ? null : OffsetDateTime.parse(track.releaseDate()).toLocalDate();
        return new Song("itunes.apple.com:" + track.trackId(), track.trackName(), null, track.artistName(), null,
            track.collectionName(), track.primaryGenreName(), releaseDate, track.artworkUrl100(), track.previewUrl(),
            null, track.trackViewUrl());
    }

    private boolean isApplePreview(String value) {
        return hasHost(value, "audio-ssl.itunes.apple.com");
    }

    private boolean isAppleArtwork(String value) {
        return hasHost(value, "mzstatic.com");
    }

    private boolean isAppleTrackPage(String value) {
        return hasHost(value, "music.apple.com");
    }

    private boolean hasHost(String value, String allowedHost) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase(allowedHost) || host.toLowerCase(Locale.ROOT).endsWith("." + allowedHost));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record SearchResponse(List<AppleTrack> results) {}
    private record AppleTrack(Long trackId, String trackName, String artistName, String collectionName,
                              String primaryGenreName, String releaseDate, String artworkUrl100,
                              String previewUrl, String trackViewUrl) {}
}
