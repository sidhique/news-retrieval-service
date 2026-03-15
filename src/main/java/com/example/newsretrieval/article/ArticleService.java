package com.example.newsretrieval.article;

import com.example.newsretrieval.gemini.GeminiService;
import com.example.newsretrieval.openai.OpenAiService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final GeminiService geminiService;

    public ArticleService(ArticleRepository articleRepository, GeminiService geminiService) {
        this.articleRepository = articleRepository;
        this.geminiService = geminiService;
    }

    @Transactional
    public String upsertArticle(ArticleUpsertRequest request) {
        UUID articleId = Objects.requireNonNull(request.id(), "id is required.");
        String aiSummary = StringUtils.hasText(request.aiSummary())
            ? request.aiSummary()
            : geminiService.summarizeArticle(request.title(), request.description());
        ArticleEntity entity = articleRepository.findById(articleId).orElseGet(ArticleEntity::new);
        entity.setId(articleId);
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setUrl(request.url());
        entity.setPublicationDate(request.publicationDate());
        entity.setSourceName(request.sourceName());
        entity.setCategory(request.category().toArray(String[]::new));
        entity.setRelevanceScore(request.relevanceScore());
        entity.setLatitude(request.latitude());
        entity.setLongitude(request.longitude());
        entity.setAiSummary(aiSummary);
        ArticleEntity saved = articleRepository.save(entity);
        articleRepository.syncLocationPoint(saved.getId());
        return aiSummary;
    }

    @Transactional
    public List<ArticleUpsertResult> upsertArticles(List<ArticleUpsertRequest> requests) {
        return requests.stream()
            .map(request -> new ArticleUpsertResult(request.id(), upsertArticle(request)))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Article> getAllArticles() {
        List<ArticleEntity> entities = articleRepository.findAll(
            Sort.by(
                Sort.Order.desc("publicationDate").nullsLast(),
                Sort.Order.desc("createdAt")
            )
        );
        return toArticles(entities);
    }

    @Transactional(readOnly = true)
    public List<Article> getArticlesByCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw new IllegalArgumentException("category is required.");
        }
        return toArticles(articleRepository.findAllByCategory(category.trim()));
    }

    @Transactional(readOnly = true)
    public List<Article> getArticlesByRelevanceScore(double threshold) {
        return toArticles(articleRepository.findAllByRelevanceScoreGreaterThan(threshold));
    }

    @Transactional(readOnly = true)
    public List<Article> getArticlesBySource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("source is required.");
        }
        return toArticles(articleRepository.findAllBySource(source.trim()));
    }

    @Transactional(readOnly = true)
    public List<Article> getArticlesNearby(double latitude, double longitude, double radiusKm) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180.");
        }
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be greater than 0.");
        }
        return toArticles(articleRepository.findAllNearby(latitude, longitude, radiusKm));
    }

    @Transactional(readOnly = true)
    public List<Article> searchArticles(
        String query,
        OpenAiService.SearchCriteria criteria,
        Double latitude,
        Double longitude,
        double radiusKm
    ) {
        Objects.requireNonNull(criteria, "Search criteria must be provided.");
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query is required.");
        }
        if (radiusKm <= 0) {
            throw new IllegalArgumentException("radiusKm must be greater than 0.");
        }

        boolean nearbyIntent = criteria.hasIntent("nearby");
        boolean sourceIntent = criteria.hasIntent("source");
        boolean categoryIntent = criteria.hasIntent("category");
        boolean textIntent = criteria.hasIntent("text");

        boolean hasLatitude = latitude != null;
        boolean hasLongitude = longitude != null;
        if (hasLatitude ^ hasLongitude) {
            throw new IllegalArgumentException("Both latitude and longitude must be provided together.");
        }
        if (nearbyIntent && (!hasLatitude || !hasLongitude)) {
            throw new IllegalArgumentException("location is required when intent is nearby.");
        }

        String normalizedQuery = query.trim();
        String source = criteria.source();
        String category = criteria.category();
        if (sourceIntent && !StringUtils.hasText(source)) {
            throw new IllegalArgumentException("source intent detected but source could not be extracted from query.");
        }
        if (categoryIntent && !StringUtils.hasText(category)) {
            throw new IllegalArgumentException("category intent detected but category could not be extracted from query.");
        }

        boolean applyNearby = nearbyIntent || (hasLatitude && hasLongitude);
        double lat = 0;
        double lon = 0;
        if (applyNearby) {
            lat = Objects.requireNonNull(latitude);
            lon = Objects.requireNonNull(longitude);
            if (lat < -90 || lat > 90) {
                throw new IllegalArgumentException("latitude must be between -90 and 90.");
            }
            if (lon < -180 || lon > 180) {
                throw new IllegalArgumentException("longitude must be between -180 and 180.");
            }
        }

        boolean applyText = textIntent || (!sourceIntent && !categoryIntent && !nearbyIntent);
        String textSearchQuery = normalizedQuery;
        if (textIntent && StringUtils.hasText(criteria.searchTerm())) {
            textSearchQuery = criteria.searchTerm().trim();
        }
        String normalizedSource = StringUtils.hasText(source) ? source.trim() : "";
        String normalizedCategory = StringUtils.hasText(category) ? category.trim() : "";

        List<ArticleRepository.SearchResultRow> scoredRows = articleRepository.searchByCriteriaWithScore(
            textSearchQuery,
            normalizedSource,
            normalizedCategory,
            lat,
            lon,
            radiusKm,
            sourceIntent,
            categoryIntent,
            applyText,
            applyNearby
        );

        if (scoredRows.isEmpty()) {
            return List.of();
        }

        return scoredRows.stream()
            .map(this::toArticle)
            .toList();
    }

    private List<String> toCategoryList(String[] values) {
        if (values == null) {
            return List.of();
        }
        return Arrays.stream(values)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private List<Article> toArticles(List<ArticleEntity> entities) {
        return entities.stream().map(this::toArticle).toList();
    }

    private Article toArticle(ArticleEntity entity) {
        return new Article(
            entity.getId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getUrl(),
            entity.getPublicationDate(),
            entity.getSourceName(),
            toCategoryList(entity.getCategory()),
            entity.getRelevanceScore(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getAiSummary(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private Article toArticle(ArticleRepository.SearchResultRow row) {
        return new Article(
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
            toOffsetDateTime(row.getCreatedAt()),
            toOffsetDateTime(row.getUpdatedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC);
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

}
