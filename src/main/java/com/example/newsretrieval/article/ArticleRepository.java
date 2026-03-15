package com.example.newsretrieval.article;

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
}
