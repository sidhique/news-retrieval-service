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
            SET location_point = POINT(longitude, latitude)
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
}
