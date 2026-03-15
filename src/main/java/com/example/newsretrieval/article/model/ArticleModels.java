package com.example.newsretrieval.article.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ArticleModels {

    private ArticleModels() {
    }

    public record ArticleUpsertRequest(
        UUID id,
        String title,
        String description,
        String url,
        LocalDateTime publicationDate,
        String sourceName,
        List<String> category,
        Double relevanceScore,
        String aiSummary,
        Double latitude,
        Double longitude
    ) {
    }

    public record ArticleUpsertResult(
        UUID id,
        String aiSummary
    ) {
    }

    public record Article(
        UUID id,
        String title,
        String description,
        String url,
        LocalDateTime publicationDate,
        String sourceName,
        List<String> category,
        Double relevanceScore,
        Double latitude,
        Double longitude,
        String aiSummary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record ArticlePage(
        List<Article> articles,
        long totalCount
    ) {
    }

    public record TrendingArticle(
        Article article,
        double trendingScore
    ) {
    }

    public record TrendingFeed(
        List<TrendingArticle> items,
        long totalCount,
        int limit,
        int offset,
        Instant generatedAt
    ) {
    }

    public record EventUpsertRequest(
        UUID userId,
        UUID articleId,
        String eventType,
        Double latitude,
        Double longitude,
        OffsetDateTime occurredAt
    ) {
    }
}
