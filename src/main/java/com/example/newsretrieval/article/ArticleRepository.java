package com.example.newsretrieval.article;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<ArticleEntity, UUID> {

    @Modifying
    @Query(
        value = """
            UPDATE articles
            SET location_point = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
            WHERE id = :id
            """,
        nativeQuery = true
    )
    void syncLocationPoint(@Param("id") UUID id);

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE EXISTS (
                SELECT 1
                FROM unnest(category) AS c
                WHERE lower(c) = lower(:category)
            )
            ORDER BY publication_date DESC NULLS LAST, created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> findAllByCategory(@Param("category") String category);

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE relevance_score IS NOT NULL
              AND relevance_score > :threshold
            ORDER BY relevance_score DESC, publication_date DESC NULLS LAST, created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> findAllByRelevanceScoreGreaterThan(@Param("threshold") double threshold);

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE source_name IS NOT NULL
              AND lower(source_name) = lower(:source)
            ORDER BY publication_date DESC NULLS LAST, created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> findAllBySource(@Param("source") String source);

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE location_point IS NOT NULL
              AND ST_DWithin(
                    location_point,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radiusKm * 1000
                  )
            ORDER BY
              ST_Distance(
                location_point,
                ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
              ) ASC,
              publication_date DESC NULLS LAST,
              created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> findAllNearby(
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm
    );

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE to_tsvector(
                    'english',
                    coalesce(title, '') || ' ' ||
                    coalesce(description, '') || ' ' ||
                    coalesce(source_name, '') || ' ' ||
                    coalesce(array_to_string(category, ' '), '')
                  )
                  @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(
                       to_tsvector(
                           'english',
                           coalesce(title, '') || ' ' ||
                           coalesce(description, '') || ' ' ||
                           coalesce(source_name, '') || ' ' ||
                           coalesce(array_to_string(category, ' '), '')
                       ),
                       plainto_tsquery('english', :query)
                     ) DESC,
                     publication_date DESC NULLS LAST,
                     created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> searchByTextMatch(@Param("query") String query);

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE to_tsvector(
                    'english',
                    coalesce(title, '') || ' ' ||
                    coalesce(description, '') || ' ' ||
                    coalesce(source_name, '') || ' ' ||
                    coalesce(array_to_string(category, ' '), '')
                  )
                  @@ plainto_tsquery('english', :query)
              AND location_point IS NOT NULL
              AND ST_DWithin(
                    location_point,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radiusKm * 1000
                  )
            ORDER BY ts_rank(
                       to_tsvector(
                           'english',
                           coalesce(title, '') || ' ' ||
                           coalesce(description, '') || ' ' ||
                           coalesce(source_name, '') || ' ' ||
                           coalesce(array_to_string(category, ' '), '')
                       ),
                       plainto_tsquery('english', :query)
                     ) DESC,
                     ST_Distance(
                       location_point,
                       ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                     ) ASC,
                     publication_date DESC NULLS LAST,
                     created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> searchByTextMatchNearby(
        @Param("query") String query,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm
    );

    @Query(
        value = """
            SELECT *
            FROM articles
            WHERE (:applySource = false OR (
                      source_name IS NOT NULL
                      AND lower(source_name) = lower(:source)
                  ))
              AND (:applyCategory = false OR EXISTS (
                      SELECT 1
                      FROM unnest(category) AS c
                      WHERE lower(c) = lower(:category)
                  ))
              AND (:applyText = false OR (
                      to_tsvector(
                          'english',
                          coalesce(title, '') || ' ' ||
                          coalesce(description, '') || ' ' ||
                          coalesce(source_name, '') || ' ' ||
                          coalesce(array_to_string(category, ' '), '')
                      ) @@ plainto_tsquery('english', :query)
                  ))
              AND (:applyNearby = false OR (
                      location_point IS NOT NULL
                      AND ST_DWithin(
                          location_point,
                          ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                          :radiusKm * 1000
                      )
                  ))
            ORDER BY
              CASE
                WHEN :applyText THEN ts_rank(
                    to_tsvector(
                        'english',
                        coalesce(title, '') || ' ' ||
                        coalesce(description, '') || ' ' ||
                        coalesce(source_name, '') || ' ' ||
                        coalesce(array_to_string(category, ' '), '')
                    ),
                    plainto_tsquery('english', :query)
                )
                ELSE 0
              END DESC,
              CASE
                WHEN :applyNearby THEN ST_Distance(
                    location_point,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                )
                ELSE 0
              END ASC,
              publication_date DESC NULLS LAST,
              created_at DESC
            """,
        nativeQuery = true
    )
    List<ArticleEntity> searchByCriteria(
        @Param("query") String query,
        @Param("source") String source,
        @Param("category") String category,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm,
        @Param("applySource") boolean applySource,
        @Param("applyCategory") boolean applyCategory,
        @Param("applyText") boolean applyText,
        @Param("applyNearby") boolean applyNearby
    );

    interface SearchResultRow {
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
        Double getFinalScore();
    }

    @Query(
        value = """
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
              LEAST(GREATEST(COALESCE(a.relevance_score, 0), 0), 1) AS finalScore
            FROM articles a
            WHERE (:applySource = false OR (
                      a.source_name IS NOT NULL
                      AND lower(a.source_name) = lower(:source)
                  ))
              AND (:applyCategory = false OR EXISTS (
                      SELECT 1
                      FROM unnest(a.category) AS c
                      WHERE lower(c) = lower(:category)
                  ))
              AND (:applyText = false OR (
                      to_tsvector(
                          'english',
                          coalesce(a.title, '') || ' ' ||
                          coalesce(a.description, '') || ' ' ||
                          coalesce(a.source_name, '') || ' ' ||
                          coalesce(array_to_string(a.category, ' '), '')
                      ) @@ plainto_tsquery('english', :query)
                  ))
              AND (:applyNearby = false OR (
                      a.location_point IS NOT NULL
                      AND ST_DWithin(
                          a.location_point,
                          ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                          :radiusKm * 1000
                      )
                  ))
            ORDER BY
              finalScore DESC,
              CASE
                WHEN :applyText THEN ts_rank(
                    to_tsvector(
                        'english',
                        coalesce(a.title, '') || ' ' ||
                        coalesce(a.description, '') || ' ' ||
                        coalesce(a.source_name, '') || ' ' ||
                        coalesce(array_to_string(a.category, ' '), '')
                    ),
                    plainto_tsquery('english', :query)
                )
                ELSE 0
              END DESC,
              CASE
                WHEN :applyNearby THEN ST_Distance(
                    a.location_point,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                )
                ELSE 0
              END ASC,
              a.publication_date DESC NULLS LAST,
              a.created_at DESC
            """,
        nativeQuery = true
    )
    List<SearchResultRow> searchByCriteriaWithScore(
        @Param("query") String query,
        @Param("source") String source,
        @Param("category") String category,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm,
        @Param("applySource") boolean applySource,
        @Param("applyCategory") boolean applyCategory,
        @Param("applyText") boolean applyText,
        @Param("applyNearby") boolean applyNearby
    );
}
