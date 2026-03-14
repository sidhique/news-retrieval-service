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

    public record QueryAnalysis(
        List<String> entities,
        List<String> keyConcepts,
        List<String> intents
    ) {
    }
}
