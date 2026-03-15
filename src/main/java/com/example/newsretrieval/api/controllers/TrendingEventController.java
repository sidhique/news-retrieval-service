package com.example.newsretrieval.api.controllers;

import com.example.newsretrieval.api.dto.TrendingEventDtos.EventUpsertPayload;
import com.example.newsretrieval.api.dto.TrendingEventDtos.EventUpsertResponse;
import com.example.newsretrieval.article.model.ArticleModels.EventUpsertRequest;
import com.example.newsretrieval.article.service.TrendingService;
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
        EventUpsertRequest request = new EventUpsertRequest(
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
}
