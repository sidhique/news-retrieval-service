package com.example.newsretrieval.services.location;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class LocationGeocodingService {

    private final RestClient restClient;

    public LocationGeocodingService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://nominatim.openstreetmap.org").build();
    }

    @SuppressWarnings("null")
    public Coordinates geocode(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("location is required.");
        }

        JsonNode responseBody;
        try {
            responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .queryParam("q", location.trim())
                    .build())
                .header("User-Agent", "news-retrieval-app/1.0")
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new RuntimeException("Location lookup failed: " + ex.getResponseBodyAsString(), ex);
        }

        if (responseBody == null || !responseBody.isArray() || responseBody.isEmpty()) {
            throw new IllegalArgumentException("Unable to resolve location: " + location);
        }

        JsonNode bestMatch = responseBody.path(0);
        String latitudeText = bestMatch.path("lat").asText();
        String longitudeText = bestMatch.path("lon").asText();
        if (!StringUtils.hasText(latitudeText) || !StringUtils.hasText(longitudeText)) {
            throw new IllegalArgumentException("Unable to resolve location coordinates: " + location);
        }

        return new Coordinates(
            Double.parseDouble(latitudeText),
            Double.parseDouble(longitudeText)
        );
    }

    public record Coordinates(double latitude, double longitude) {
    }
}
