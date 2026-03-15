package com.example.newsretrieval.api.controllers;

import com.example.newsretrieval.services.gemini.GeminiService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/gemini/query")
    public ResponseEntity<GeminiQueryResponse> query(@RequestBody GeminiQueryRequest request) {
        GeminiService.QueryAnalysis analysis = geminiService.analyzeQuery(request.query());
        return ResponseEntity.ok(
            new GeminiQueryResponse(
                analysis.entities(),
                analysis.keyConcepts(),
                analysis.intents()
            )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, RuntimeException.class})
    public ResponseEntity<ErrorResponse> handleGeminiErrors(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    public record GeminiQueryRequest(String query) {
    }

    public record GeminiQueryResponse(
        List<String> entities,
        List<String> keyConcepts,
        List<String> intents
    ) {
    }

    public record ErrorResponse(String error) {
    }
}
