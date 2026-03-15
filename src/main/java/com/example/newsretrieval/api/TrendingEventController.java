package com.example.newsretrieval.api;

import com.example.newsretrieval.article.TrendingService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TrendingEventController {

    private final TrendingService trendingService;

    public TrendingEventController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventUpsertResponse> createEvent(@RequestBody EventUpsertPayload payload) {
        TrendingService.EventUpsertRequest request = new TrendingService.EventUpsertRequest(
            payload.userId(),
            payload.articleId(),
            payload.eventType(),
            payload.latitude(),
            payload.longitude(),
            payload.occurredAt()
        );
        trendingService.recordEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EventUpsertResponse("accepted"));
    }

    public record EventUpsertPayload(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("article_id") UUID articleId,
        @JsonProperty("event_type") String eventType,
        Double latitude,
        Double longitude,
        @JsonProperty("occurred_at") OffsetDateTime occurredAt
    ) {
    }

    public record EventUpsertResponse(String status) {
    }
}
