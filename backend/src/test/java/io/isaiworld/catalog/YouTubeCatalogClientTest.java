package io.isaiworld.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YouTubeCatalogClientTest {
    @Test
    void mapsEmbeddableTamilVideoResultsToProviderSongs() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("youtube/v3/search")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("key", "test-key"))
            .andExpect(queryParam("videoEmbeddable", "true"))
            .andRespond(withSuccess("""
                {
                  "items": [{
                    "id": {"videoId": "abcdefghijk"},
                    "snippet": {
                      "title": "தமிழ் பாடல்",
                      "channelTitle": "Official Music",
                      "publishedAt": "2026-10-20T10:15:00Z",
                      "thumbnails": {"high": {"url": "https://img.youtube.com/cover.jpg"}}
                    }
                  }]
                }
                """, MediaType.APPLICATION_JSON));

        var client = new YouTubeCatalogClient("test-key", builder);
        var songs = client.searchTamilMusic();

        assertEquals(1, songs.size());
        assertEquals("abcdefghijk", songs.getFirst().id());
        assertEquals("தமிழ் பாடல்", songs.getFirst().title());
        assertEquals("YouTube channel: Official Music", songs.getFirst().singer());
        assertEquals("https://www.youtube.com/embed/abcdefghijk", songs.getFirst().playbackUrl());
        assertEquals("https://img.youtube.com/cover.jpg", songs.getFirst().artworkUrl());
        assertEquals("2026-10-20", songs.getFirst().releaseDate().toString());
        server.verify();
    }

    @Test
    void rejectsSyncWithoutAnApiKey() {
        var client = new YouTubeCatalogClient("", RestClient.builder());

        assertFalse(client.configured());
        assertThrows(IllegalStateException.class, client::searchTamilMusic);
    }
}
