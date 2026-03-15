package com.example.newsretrieval.article;

import com.example.newsretrieval.gemini.GeminiService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
        String aiSummary = geminiService.summarizeArticle(request.title(), request.description());
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
        return entities.stream()
            .map(entity -> new Article(
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
            ))
            .toList();
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
        Double latitude,
        Double longitude
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
