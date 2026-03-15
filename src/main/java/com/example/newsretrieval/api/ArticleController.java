package com.example.newsretrieval.api;

import com.example.newsretrieval.article.ArticleService;
import com.example.newsretrieval.location.LocationGeocodingService;
import com.example.newsretrieval.openai.OpenAiService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {

    private static final DateTimeFormatter PUBLICATION_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'");

    private final ArticleService articleService;
    private final OpenAiService openAiService;
    private final LocationGeocodingService locationGeocodingService;

    public ArticleController(
        ArticleService articleService,
        OpenAiService openAiService,
        LocationGeocodingService locationGeocodingService
    ) {
        this.articleService = articleService;
        this.openAiService = openAiService;
        this.locationGeocodingService = locationGeocodingService;
    }

    @GetMapping("/articles")
    public ResponseEntity<ArticlesResponse> getArticles() {
        return ResponseEntity.ok(new ArticlesResponse(toArticleResponsesFromArticles(articleService.getAllArticles())));
    }

    @GetMapping("/api/news/category")
    public ResponseEntity<ArticlesResponse> getArticlesByCategory(@RequestParam("category") String category) {
        List<ArticleService.Article> articles = articleService.getArticlesByCategory(category);
        return ResponseEntity.ok(new ArticlesResponse(toArticleResponsesFromArticles(articles)));
    }

    @GetMapping("/api/news/score")
    public ResponseEntity<ArticlesResponse> getArticlesByRelevance(
        @RequestParam(name = "threshold", defaultValue = "0.7") double threshold
    ) {
        List<ArticleService.Article> articles = articleService.getArticlesByRelevanceScore(threshold);
        return ResponseEntity.ok(new ArticlesResponse(toArticleResponsesFromArticles(articles)));
    }

    @GetMapping("/api/news/source")
    public ResponseEntity<ArticlesResponse> getArticlesBySource(@RequestParam("source") String source) {
        List<ArticleService.Article> articles = articleService.getArticlesBySource(source);
        return ResponseEntity.ok(new ArticlesResponse(toArticleResponsesFromArticles(articles)));
    }

    @GetMapping("/api/news/nearby")
    public ResponseEntity<ArticlesResponse> getNearbyArticles(
        @RequestParam("latitude") double latitude,
        @RequestParam("longitude") double longitude,
        @RequestParam(name = "radiusKm", defaultValue = "10") double radiusKm
    ) {
        List<ArticleService.Article> articles = articleService.getArticlesNearby(latitude, longitude, radiusKm);
        return ResponseEntity.ok(new ArticlesResponse(toArticleResponsesFromArticles(articles)));
    }

    @GetMapping("/api/news/search")
    public ResponseEntity<SearchArticlesResponse> searchArticles(
        @RequestParam("query") String query,
        @RequestParam(name = "location", required = false) String location,
        @RequestParam(name = "radiusKm", defaultValue = "100") double radiusKm
    ) {
        OpenAiService.SearchCriteria criteria = openAiService.analyzeSearchQuery(query, location);
        Double latitude = null;
        Double longitude = null;
        if (StringUtils.hasText(location)) {
            LocationGeocodingService.Coordinates coordinates = locationGeocodingService.geocode(location);
            latitude = coordinates.latitude();
            longitude = coordinates.longitude();
        }
        List<ArticleService.Article> results = articleService.searchArticles(
            query,
            criteria,
            latitude,
            longitude,
            radiusKm
        );
        return ResponseEntity.ok(new SearchArticlesResponse(
            toArticleResponsesFromArticles(results),
            new SearchCriteriaResponse(
                criteria.intents(),
                criteria.searchTerm(),
                criteria.source(),
                criteria.category()
            )
        ));
    }

    @PostMapping("/articles")
    public ResponseEntity<ArticleUpsertResponse> upsertArticle(@RequestBody ArticleUpsertPayload payload) {
        validate(payload);
        ArticleService.ArticleUpsertRequest request = new ArticleService.ArticleUpsertRequest(
            payload.id(),
            payload.title(),
            payload.description(),
            payload.url(),
            payload.publicationDate(),
            payload.sourceName(),
            payload.category(),
            payload.relevanceScore(),
            payload.aiSummary(),
            payload.latitude(),
            payload.longitude()
        );
        String aiSummary = articleService.upsertArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ArticleUpsertResponse(payload.id(), aiSummary));
    }

    @PostMapping("/articles/batch")
    public ResponseEntity<ArticleBatchUpsertResponse> upsertArticles(@RequestBody List<ArticleUpsertPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            throw new IllegalArgumentException("payload list must not be empty.");
        }

        List<ArticleService.ArticleUpsertRequest> requests = payloads.stream()
            .peek(this::validate)
            .map(payload -> new ArticleService.ArticleUpsertRequest(
                payload.id(),
                payload.title(),
                payload.description(),
                payload.url(),
                payload.publicationDate(),
                payload.sourceName(),
                payload.category(),
                payload.relevanceScore(),
                payload.aiSummary(),
                payload.latitude(),
                payload.longitude()
            ))
            .toList();

        List<ArticleUpsertResponse> responses = articleService.upsertArticles(requests).stream()
            .map(result -> new ArticleUpsertResponse(result.id(), result.aiSummary()))
            .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(new ArticleBatchUpsertResponse(responses));
    }

    private void validate(ArticleUpsertPayload payload) {
        if (payload.id() == null) {
            throw new IllegalArgumentException("id is required.");
        }
        if (!StringUtils.hasText(payload.title())) {
            throw new IllegalArgumentException("title is required.");
        }
        if (payload.latitude() == null || payload.longitude() == null) {
            throw new IllegalArgumentException("latitude and longitude are required.");
        }
        if (payload.relevanceScore() == null) {
            throw new IllegalArgumentException("relevance_score is required.");
        }
        if (payload.category() == null) {
            throw new IllegalArgumentException("category is required.");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, RuntimeException.class})
    public ResponseEntity<ErrorResponse> handleServerErrors(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
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

    public record ArticlesResponse(List<ArticleResponse> articles) {
    }

    public record SearchArticlesResponse(
        List<ArticleResponse> articles,
        @JsonProperty("search_criteria") SearchCriteriaResponse searchCriteria
    ) {
    }

    public record SearchCriteriaResponse(
        List<String> intents,
        @JsonProperty("search_term") String searchTerm,
        String source,
        String category
    ) {
    }

    public record ErrorResponse(String error) {
    }

    private String formatPublicationDate(LocalDateTime publicationDate) {
        if (publicationDate == null) {
            return null;
        }
        return publicationDate.atOffset(ZoneOffset.UTC).format(PUBLICATION_DATE_FORMATTER);
    }

    private String formatCategory(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return String.join(", ", categories);
    }

    private List<ArticleResponse> toArticleResponsesFromArticles(List<ArticleService.Article> articles) {
        return articles.stream()
            .map(article -> new ArticleResponse(
                article.title(),
                article.description(),
                article.url(),
                formatPublicationDate(article.publicationDate()),
                article.sourceName(),
                formatCategory(article.category()),
                article.relevanceScore(),
                article.aiSummary(),
                article.latitude(),
                article.longitude()
            ))
            .toList();
    }
}
