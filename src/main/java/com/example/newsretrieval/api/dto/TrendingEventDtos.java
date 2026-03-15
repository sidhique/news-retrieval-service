package com.example.newsretrieval.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class TrendingEventDtos {

    private TrendingEventDtos() {
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
