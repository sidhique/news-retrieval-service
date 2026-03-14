package com.example.newsretrieval.api;

import com.example.newsretrieval.article.ArticleService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
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
            payload.latitude(),
            payload.longitude()
        );
        String aiSummary = articleService.upsertArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ArticleUpsertResponse(payload.id(), aiSummary));
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
        Double latitude,
        Double longitude
    ) {
    }

    public record ArticleUpsertResponse(UUID id, @JsonProperty("ai_summary") String aiSummary) {
    }

    public record ErrorResponse(String error) {
    }
}
