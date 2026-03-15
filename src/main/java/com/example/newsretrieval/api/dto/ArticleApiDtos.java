package com.example.newsretrieval.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ArticleApiDtos {

    private ArticleApiDtos() {
    }

    public record ArticleUpsertPayload(
        UUID id,
        String title,
        String description,
        String url,
        @JsonProperty("publication_date") LocalDateTime publicationDate,
        @JsonProperty("source_name") String sourceName,
        List<String> category,
        @JsonProperty("relevance_score") Double relevanceScore,
        @JsonProperty("ai_summary") String aiSummary,
        Double latitude,
        Double longitude
    ) {
    }

    public record ArticleUpsertResponse(UUID id, @JsonProperty("ai_summary") String aiSummary) {
    }

    public record ArticleBatchUpsertResponse(List<ArticleUpsertResponse> articles) {
    }

    public record ArticleResponse(
        String title,
        String description,
        String url,
        @JsonProperty("publication_date") String publicationDate,
        @JsonProperty("source_name") String sourceName,
        String category,
        @JsonProperty("relevance_score") Double relevanceScore,
        @JsonProperty("llm_summary") String llmSummary,
        Double latitude,
        Double longitude
    ) {
    }

    public record PaginationResponse(
        int offset,
        int limit,
        long count
    ) {
    }

    public record ArticlesResponse(
        List<ArticleResponse> articles,
        PaginationResponse pagination
    ) {
    }

    public record SearchCriteriaResponse(
        List<String> intents,
        @JsonProperty("search_term") String searchTerm,
        String source,
        String category
    ) {
    }

    public record SearchArticlesResponse(
        List<ArticleResponse> articles,
        PaginationResponse pagination,
        @JsonProperty("search_criteria") SearchCriteriaResponse searchCriteria
    ) {
    }

    public record TrendingArticleResponse(
        String title,
        String description,
        String url,
        @JsonProperty("publication_date") String publicationDate,
        @JsonProperty("source_name") String sourceName,
        String category,
        @JsonProperty("relevance_score") Double relevanceScore,
        @JsonProperty("llm_summary") String llmSummary,
        Double latitude,
        Double longitude,
        @JsonProperty("trending_score") Double trendingScore
    ) {
    }

    public record TrendingFeedResponse(
        List<TrendingArticleResponse> articles,
        PaginationResponse pagination
    ) {
    }

    public record ErrorResponse(String error) {
    }
}
