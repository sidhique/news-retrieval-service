package com.example.newsretrieval.services.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiService {

    private final RestClient restClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    public GeminiService(
        RestClient.Builder restClientBuilder,
        GeminiProperties geminiProperties,
        ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("null")
    public QueryAnalysis analyzeQuery(String userQuery) {
        validateGeminiConfiguration();
        if (!StringUtils.hasText(userQuery)) {
            throw new IllegalArgumentException("Query must not be empty.");
        }

        String prompt = """
            You are an extraction engine.
            Extract:
            1) entities (people, organizations, locations, events)
            2) key concepts
            3) user intents for retrieval strategy

            Return ONLY valid JSON with this exact shape:
            {
              "entities": ["..."],
              "keyConcepts": ["..."],
              "intents": ["..."]
            }

            Rules:
            - Keep values concise.
            - Intents should be short labels like "nearby", "source", "category", "latest".
            - If something is missing, return an empty array for that field.

            Query:
            """ + userQuery;

        String responseText = generateContent(prompt, "application/json");
        return parseAnalysis(responseText);
    }

    @SuppressWarnings("null")
    public String summarizeArticle(String title, String description) {
        validateGeminiConfiguration();
        if (!StringUtils.hasText(title) && !StringUtils.hasText(description)) {
            throw new IllegalArgumentException("At least one of title or description must be provided.");
        }

        String prompt = """
            You are a news summarization assistant.
            Create a concise summary in 2-3 sentences.
            Focus on key facts and avoid speculation.
            Return plain text only.

            Title:
            """ + (StringUtils.hasText(title) ? title : "N/A") + """

            Description:
            """ + (StringUtils.hasText(description) ? description : "N/A");

        return generateContent(prompt, null).trim();
    }

    private void validateGeminiConfiguration() {
        if (!StringUtils.hasText(geminiProperties.getApiKey())) {
            throw new IllegalStateException("Gemini API key is missing. Configure gemini.api-key or GEMINI_API_KEY.");
        }
        if (!StringUtils.hasText(geminiProperties.getModel())) {
            throw new IllegalStateException("Gemini model is missing. Configure gemini.model.");
        }
    }

    @SuppressWarnings("null")
    private String generateContent(String prompt, String responseMimeType) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(
            "contents",
            List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );
        if (StringUtils.hasText(responseMimeType)) {
            requestBody.put("generationConfig", Map.of("responseMimeType", responseMimeType));
        }

        JsonNode responseBody;
        try {
            responseBody = restClient.post()
                .uri(
                    "/v1beta/models/{model}:generateContent?key={apiKey}",
                    geminiProperties.getModel(),
                    geminiProperties.getApiKey()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new RuntimeException("Gemini API request failed: " + ex.getResponseBodyAsString(), ex);
        }

        if (responseBody == null) {
            throw new RuntimeException("Gemini returned an empty response.");
        }

        JsonNode textNode = responseBody.path("candidates")
            .path(0)
            .path("content")
            .path("parts")
            .path(0)
            .path("text");

        if (!StringUtils.hasText(textNode.asText())) {
            throw new RuntimeException("Gemini response did not contain text content.");
        }

        return textNode.asText();
    }

    private QueryAnalysis parseAnalysis(String rawText) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawText));
            return new QueryAnalysis(
                readStringArray(root, "entities"),
                readStringArray(root, "keyConcepts"),
                readStringArray(root, "intents")
            );
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to parse Gemini JSON response: " + rawText, ex);
        }
    }

    private String extractJson(String rawText) {
        String trimmed = rawText.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > -1 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private List<String> readStringArray(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    public record QueryAnalysis(
        List<String> entities,
        List<String> keyConcepts,
        List<String> intents
    ) {
    }
}
