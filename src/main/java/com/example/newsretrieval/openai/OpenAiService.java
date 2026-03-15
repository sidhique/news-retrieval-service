package com.example.newsretrieval.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenAiService {

    private static final String SYSTEM_PROMPT = """
        You are an extraction engine.
        Extract:
        1) entities (people, organizations, locations, events)
        2) key concepts
        3) user intents for retrieval strategy

        Rules:
        - Keep values concise.
        - Intents should be short labels like "nearby", "source", "category", "latest".
        - If something is missing, return an empty array for that field.
        """;
    private static final String SEARCH_SYSTEM_PROMPT = """
        You extract search filters for a news retrieval system.
        Return structured values only based on user intent.

        Field guidance:
        - intents: list from ["nearby","source","category","latest","text"]
        - keywords: concise list of useful search terms from the query
        - source: specific publisher name like "Reuters", "New York Times"
        - category: specific category like "technology", "sports", "national"

        Leave unknown values null.
        """;

    private final OpenAiProperties openAiProperties;
    private final OpenAIClient openAIClient;

    public OpenAiService(OpenAiProperties openAiProperties) {
        this.openAiProperties = openAiProperties;
        if (StringUtils.hasText(openAiProperties.getApiKey())) {
            this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(openAiProperties.getApiKey())
                .build();
        } else {
            this.openAIClient = null;
        }
    }

    public QueryAnalysis analyzeQuery(String userQuery) {
        if (!StringUtils.hasText(openAiProperties.getApiKey())) {
            throw new IllegalStateException("OpenAI API key is missing. Configure openai.api-key or OPENAI_API_KEY.");
        }
        if (!StringUtils.hasText(openAiProperties.getModel())) {
            throw new IllegalStateException("OpenAI model is missing. Configure openai.model.");
        }
        if (!StringUtils.hasText(userQuery)) {
            throw new IllegalArgumentException("Query must not be empty.");
        }

        StructuredChatCompletionCreateParams<StructuredQueryAnalysis> params = ChatCompletionCreateParams.builder()
            .model(openAiProperties.getModel())
            .addSystemMessage(SYSTEM_PROMPT)
            .addUserMessage(userQuery)
            .responseFormat(StructuredQueryAnalysis.class)
            .build();

        List<StructuredQueryAnalysis> outputs = openAIClient.chat().completions().create(params)
            .choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .toList();

        if (outputs.isEmpty()) {
            throw new RuntimeException("OpenAI returned no structured content.");
        }

        StructuredQueryAnalysis result = outputs.get(0);
        return new QueryAnalysis(
            normalize(result.entities),
            normalize(result.keyConcepts),
            normalize(result.intents)
        );
    }

    public SearchCriteria analyzeSearchQuery(String query, String location) {
        validateConfigurationAndQuery(query);
        String prompt = """
            User query:
            """ + query + """

            Optional user location context:
            """ + (StringUtils.hasText(location) ? location : "N/A");

        StructuredChatCompletionCreateParams<StructuredSearchCriteria> params = ChatCompletionCreateParams.builder()
            .model(openAiProperties.getModel())
            .addSystemMessage(SEARCH_SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .responseFormat(StructuredSearchCriteria.class)
            .build();

        List<StructuredSearchCriteria> outputs = openAIClient.chat().completions().create(params)
            .choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .toList();

        if (outputs.isEmpty()) {
            throw new RuntimeException("OpenAI returned no structured search criteria.");
        }

        StructuredSearchCriteria raw = outputs.get(0);
        List<String> keywords = normalize(raw.keywords);
        if (keywords.isEmpty()) {
            keywords = normalize(List.of(query));
        }

        return new SearchCriteria(
            normalize(raw.intents),
            keywords,
            normalizeSingle(raw.source),
            normalizeSingle(raw.category)
        );
    }

    private void validateConfigurationAndQuery(String query) {
        if (!StringUtils.hasText(openAiProperties.getApiKey())) {
            throw new IllegalStateException("OpenAI API key is missing. Configure openai.api-key or OPENAI_API_KEY.");
        }
        if (!StringUtils.hasText(openAiProperties.getModel())) {
            throw new IllegalStateException("OpenAI model is missing. Configure openai.model.");
        }
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required.");
        }
    }

    private String normalizeSingle(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalize(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }

    public static final class StructuredQueryAnalysis {
        public List<String> entities;
        public List<String> keyConcepts;
        public List<String> intents;
    }

    public static final class StructuredSearchCriteria {
        public List<String> intents;
        public List<String> keywords;
        public String source;
        public String category;
    }

    public record QueryAnalysis(
        List<String> entities,
        List<String> keyConcepts,
        List<String> intents
    ) {
    }

    public record SearchCriteria(
        List<String> intents,
        List<String> keywords,
        String source,
        String category
    ) {
        public boolean hasIntent(String intent) {
            if (intents == null || intent == null) {
                return false;
            }
            return intents.stream().anyMatch(value -> intent.equalsIgnoreCase(value));
        }
    }
}
