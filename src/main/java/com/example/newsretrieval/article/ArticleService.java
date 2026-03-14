package com.example.newsretrieval.article;

import com.example.newsretrieval.gemini.GeminiService;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ArticleService {

    private static final String UPSERT_SQL = """
        INSERT INTO articles (
            id,
            title,
            description,
            url,
            publication_date,
            source_name,
            category,
            relevance_score,
            latitude,
            longitude,
            location_point,
            ai_summary,
            updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, POINT(?, ?), ?, NOW())
        ON CONFLICT (id) DO UPDATE SET
            title = EXCLUDED.title,
            description = EXCLUDED.description,
            url = EXCLUDED.url,
            publication_date = EXCLUDED.publication_date,
            source_name = EXCLUDED.source_name,
            category = EXCLUDED.category,
            relevance_score = EXCLUDED.relevance_score,
            latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            location_point = EXCLUDED.location_point,
            ai_summary = EXCLUDED.ai_summary,
            updated_at = NOW()
        """;

    private final JdbcTemplate jdbcTemplate;
    private final GeminiService geminiService;

    public ArticleService(JdbcTemplate jdbcTemplate, GeminiService geminiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.geminiService = geminiService;
    }

    public String upsertArticle(ArticleUpsertRequest request) {
        String aiSummary = geminiService.summarizeArticle(request.title(), request.description());
        jdbcTemplate.update(connection -> createStatement(connection, request, aiSummary));
        return aiSummary;
    }

    private PreparedStatement createStatement(
        Connection connection,
        ArticleUpsertRequest request,
        String aiSummary
    ) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(UPSERT_SQL);
        statement.setObject(1, request.id());
        statement.setString(2, request.title());
        statement.setString(3, request.description());
        statement.setString(4, request.url());
        statement.setTimestamp(5, toTimestamp(request.publicationDate()));
        statement.setString(6, request.sourceName());
        Array categoryArray = connection.createArrayOf("text", request.category().toArray());
        statement.setArray(7, categoryArray);
        statement.setDouble(8, request.relevanceScore());
        statement.setDouble(9, request.latitude());
        statement.setDouble(10, request.longitude());
        statement.setDouble(11, request.longitude());
        statement.setDouble(12, request.latitude());
        statement.setString(13, aiSummary);
        return statement;
    }

    private Timestamp toTimestamp(LocalDateTime publicationDate) {
        return publicationDate == null ? null : Timestamp.valueOf(publicationDate);
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
}
