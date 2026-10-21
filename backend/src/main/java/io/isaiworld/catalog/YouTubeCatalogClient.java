package io.isaiworld.catalog;

import io.isaiworld.catalog.CatalogWriter.ProviderSong;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class YouTubeCatalogClient {
    private final String apiKey;
    private final RestClient restClient;

    public YouTubeCatalogClient(@Value("${isai.youtube.api-key:}") String apiKey,
                                RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.restClient = restClientBuilder.build();
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String redactApiKey(String message) {
        return configured() ? message.replace(apiKey, "[redacted]") : message;
    }

    public List<ProviderSong> searchTamilMusic() {
        if (!configured()) {
            throw new IllegalStateException("Configure YOUTUBE_API_KEY to search YouTube.");
        }
        YouTubeResponse response = restClient.get()
            .uri(UriComponentsBuilder.fromUriString("https://www.googleapis.com/youtube/v3/search")
                .queryParam("part", "snippet")
                .queryParam("type", "video")
                .queryParam("videoEmbeddable", "true")
                .queryParam("videoSyndicated", "true")
                .queryParam("regionCode", "IN")
                .queryParam("relevanceLanguage", "ta")
                .queryParam("order", "date")
                .queryParam("maxResults", 25)
                .queryParam("q", "Tamil songs")
                .queryParam("key", apiKey)
                .build()
                .encode()
                .toUri())
            .retrieve()
            .body(YouTubeResponse.class);
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
            .filter(item -> item != null && item.id() != null && item.id().videoId() != null
                && item.id().videoId().matches("[A-Za-z0-9_-]{11}") && item.snippet() != null
                && item.snippet().title() != null && !item.snippet().title().isBlank())
            .map(item -> {
                var snippet = item.snippet();
                String artwork = snippet.thumbnails() == null ? null : snippet.thumbnails().bestUrl();
                LocalDate published = snippet.publishedAt() == null ? null : snippet.publishedAt().toLocalDate();
                return new ProviderSong(
                    item.id().videoId(),
                    snippet.title(),
                    null,
                    snippet.channelTitle() == null ? "YouTube" : "YouTube channel: " + snippet.channelTitle(),
                    null,
                    null,
                    "Tamil",
                    published,
                    artwork,
                    "https://www.youtube.com/embed/" + item.id().videoId(),
                    null);
            })
            .toList();
    }

    private record YouTubeResponse(List<SearchItem> items) {}
    private record SearchItem(VideoId id, VideoSnippet snippet) {}
    private record VideoId(String videoId) {}
    private record VideoSnippet(String title, String channelTitle, OffsetDateTime publishedAt, Thumbnails thumbnails) {}
    private record Thumbnails(Thumbnail high, Thumbnail medium, @JsonProperty("default") Thumbnail defaultThumbnail) {
        String bestUrl() {
            if (high != null) return high.url();
            if (medium != null) return medium.url();
            return defaultThumbnail == null ? null : defaultThumbnail.url();
        }
    }
    private record Thumbnail(String url) {}
}
