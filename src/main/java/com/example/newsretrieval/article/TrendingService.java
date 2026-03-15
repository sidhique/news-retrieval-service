package com.example.newsretrieval.article;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrendingService {

    private final ArticleRepository articleRepository;
    private final TrendingEventRepository trendingEventRepository;
    private final Object cacheLock = new Object();
    private final Map<String, CachedTrendingFeed> cacheByLocation = new HashMap<>();

    private final int defaultLimit;
    private final int maxLimit;
    private final double trendingRadiusKm;
    private final double cacheGridSizeDegrees;
    private final Duration cacheTtl;
    private final Duration eventRetention;

    public TrendingService(
        ArticleRepository articleRepository,
        TrendingEventRepository trendingEventRepository,
        @Value("${app.pagination.default-limit:20}") int defaultLimit,
        @Value("${app.pagination.max-limit:100}") int maxLimit,
        @Value("${app.trending.radius-km:50}") double trendingRadiusKm,
        @Value("${app.trending.cache-grid-size-degrees:0.25}") double cacheGridSizeDegrees,
        @Value("${app.trending.cache-ttl-seconds:60}") long cacheTtlSeconds,
        @Value("${app.trending.event-retention-minutes:180}") long eventRetentionMinutes
    ) {
        this.articleRepository = articleRepository;
        this.trendingEventRepository = trendingEventRepository;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.trendingRadiusKm = trendingRadiusKm;
        this.cacheGridSizeDegrees = cacheGridSizeDegrees;
        this.cacheTtl = Duration.ofSeconds(Math.max(1, cacheTtlSeconds));
        this.eventRetention = Duration.ofMinutes(Math.max(1, eventRetentionMinutes));
    }

    @Transactional
    public void recordEvent(EventUpsertRequest request) {
        Objects.requireNonNull(request, "event payload is required.");
        if (request.userId() == null) {
            throw new IllegalArgumentException("user_id is required.");
        }
        if (request.articleId() == null) {
            throw new IllegalArgumentException("article_id is required.");
        }
        UUID articleId = Objects.requireNonNull(request.articleId());
        if (!articleRepository.existsById(articleId)) {
            throw new IllegalArgumentException("article_id does not exist.");
        }
        EventType type = parseEventType(request.eventType());
        if (request.latitude() == null || request.longitude() == null) {
            throw new IllegalArgumentException("latitude and longitude are required.");
        }
        validateCoordinates(request.latitude(), request.longitude());
        OffsetDateTime occurredAt = request.occurredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.occurredAt();

        TrendingEventEntity entity = new TrendingEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(request.userId());
        entity.setArticleId(articleId);
        entity.setEventType(type.name());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setOccurredAt(occurredAt);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        trendingEventRepository.save(entity);
        trendingEventRepository.syncLocationPoint(entity.getId());
    }

    public TrendingFeed getTrendingFeed(double latitude, double longitude, Integer requestedLimit) {
        validateCoordinates(latitude, longitude);
        int limit = normalizeLimit(requestedLimit);
        String bucketKey = toBucketKey(latitude, longitude);
        Instant now = Instant.now();

        CachedTrendingFeed cached = getCached(bucketKey, now);
        if (cached != null) {
            return new TrendingFeed(cached.items(), cached.totalCount(), limit, 0, cached.cachedAt());
        }

        TrendingFeed computed = computeFeed(latitude, longitude, limit, now);
        putCached(bucketKey, computed.items(), computed.totalCount(), now);
        return computed;
    }

    private TrendingFeed computeFeed(double latitude, double longitude, int limit, Instant now) {
        OffsetDateTime cutoff = now.minus(eventRetention).atOffset(ZoneOffset.UTC);
        List<TrendingEventRepository.TrendingFeedRow> rows = trendingEventRepository.findTrendingFeed(
            latitude,
            longitude,
            trendingRadiusKm,
            cutoff,
            limit,
            0
        );
        if (rows.isEmpty()) {
            return new TrendingFeed(List.of(), 0, limit, 0, now);
        }
        List<TrendingArticle> items = new ArrayList<>();
        for (TrendingEventRepository.TrendingFeedRow row : rows) {
            items.add(new TrendingArticle(
                toArticle(row),
                row.getTrendingScore() == null ? 0.0 : row.getTrendingScore()
            ));
        }
        long totalCount = rows.get(0).getTotalCount() == null ? 0 : rows.get(0).getTotalCount();
        return new TrendingFeed(items, totalCount, limit, 0, now);
    }

    private CachedTrendingFeed getCached(String key, Instant now) {
        synchronized (cacheLock) {
            CachedTrendingFeed cached = cacheByLocation.get(key);
            if (cached == null) {
                return null;
            }
            if (Duration.between(cached.cachedAt(), now).compareTo(cacheTtl) > 0) {
                cacheByLocation.remove(key);
                return null;
            }
            return cached;
        }
    }

    private void putCached(String key, List<TrendingArticle> items, long totalCount, Instant now) {
        synchronized (cacheLock) {
            cacheByLocation.put(key, new CachedTrendingFeed(items, totalCount, now));
        }
    }

    private String toBucketKey(double latitude, double longitude) {
        long latBucket = Math.round(latitude / cacheGridSizeDegrees);
        long lonBucket = Math.round(longitude / cacheGridSizeDegrees);
        return latBucket + ":" + lonBucket;
    }

    private int normalizeLimit(Integer requestedLimit) {
        int configuredDefault = defaultLimit <= 0 ? 20 : defaultLimit;
        int candidate = requestedLimit == null || requestedLimit <= 0 ? configuredDefault : requestedLimit;
        return Math.min(candidate, maxLimit <= 0 ? 100 : maxLimit);
    }

    private EventType parseEventType(String eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("event_type is required.");
        }
        String normalized = eventType.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("event_type is required.");
        }
        try {
            return EventType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("event_type must be one of: view, click.");
        }
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("lon must be between -180 and 180.");
        }
    }

    private ArticleService.Article toArticle(TrendingEventRepository.TrendingFeedRow row) {
        return new ArticleService.Article(
            row.getId(),
            row.getTitle(),
            row.getDescription(),
            row.getUrl(),
            row.getPublicationDate(),
            row.getSourceName(),
            toCategoryList(row.getCategory()),
            row.getRelevanceScore(),
            row.getLatitude(),
            row.getLongitude(),
            row.getAiSummary(),
            row.getCreatedAt() == null ? null : row.getCreatedAt().atOffset(ZoneOffset.UTC),
            row.getUpdatedAt() == null ? null : row.getUpdatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    private List<String> toCategoryList(String[] values) {
        if (values == null) {
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                categories.add(trimmed);
            }
        }
        return categories;
    }

    private record CachedTrendingFeed(
        List<TrendingArticle> items,
        long totalCount,
        Instant cachedAt
    ) {
    }

    public record TrendingArticle(
        ArticleService.Article article,
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

    private enum EventType {
        VIEW,
        CLICK
    }
}
