package com.example.newsretrieval.article;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrendingEventRepository extends JpaRepository<TrendingEventEntity, UUID> {

    interface TrendingFeedRow {
        UUID getId();
        String getTitle();
        String getDescription();
        String getUrl();
        java.time.LocalDateTime getPublicationDate();
        String getSourceName();
        String[] getCategory();
        Double getRelevanceScore();
        Double getLatitude();
        Double getLongitude();
        String getAiSummary();
        java.time.Instant getCreatedAt();
        java.time.Instant getUpdatedAt();
        Double getTrendingScore();
        Long getTotalCount();
    }

    @Modifying
    @Query(
        value = """
            UPDATE trending_events
            SET location_point = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
            WHERE id = :id
            """,
        nativeQuery = true
    )
    void syncLocationPoint(@Param("id") UUID id);

    @Query(
        value = """
            WITH scored_events AS (
                SELECT
                  te.article_id AS article_id,
                  SUM(
                    (CASE WHEN upper(te.event_type) = 'CLICK' THEN 3.0 ELSE 1.0 END)
                    * EXP(-((EXTRACT(EPOCH FROM (NOW() - te.occurred_at)) / 60.0) / 60.0))
                    * (
                        0.5 + (
                            0.5 * (
                                1.0 - LEAST(
                                    1.0,
                                    ST_Distance(
                                        te.location_point,
                                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                                    ) / (:radiusKm * 1000.0)
                                )
                            )
                        )
                    )
                  ) AS trending_score
                FROM trending_events te
                WHERE te.occurred_at >= :cutoff
                  AND te.location_point IS NOT NULL
                  AND ST_DWithin(
                        te.location_point,
                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                        :radiusKm * 1000
                      )
                GROUP BY te.article_id
            )
            SELECT
              a.id AS id,
              a.title AS title,
              a.description AS description,
              a.url AS url,
              a.publication_date AS publicationDate,
              a.source_name AS sourceName,
              a.category AS category,
              a.relevance_score AS relevanceScore,
              a.latitude AS latitude,
              a.longitude AS longitude,
              a.ai_summary AS aiSummary,
              a.created_at AS createdAt,
              a.updated_at AS updatedAt,
              s.trending_score AS trendingScore,
              COUNT(*) OVER() AS totalCount
            FROM scored_events s
            JOIN articles a ON a.id = s.article_id
            ORDER BY s.trending_score DESC, a.publication_date DESC NULLS LAST, a.created_at DESC
            LIMIT :limit OFFSET :offset
            """,
        nativeQuery = true
    )
    List<TrendingFeedRow> findTrendingFeed(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm,
        @Param("cutoff") OffsetDateTime cutoff,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
