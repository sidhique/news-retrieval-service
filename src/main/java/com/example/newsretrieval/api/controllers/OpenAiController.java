package com.example.newsretrieval.api.controllers;

import com.example.newsretrieval.services.openai.OpenAiService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenAiController {

    private final OpenAiService openAiService;

    public OpenAiController(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @PostMapping("/openai/query")
    public ResponseEntity<OpenAiQueryResponse> query(@RequestBody OpenAiQueryRequest request) {
        OpenAiService.QueryAnalysis analysis = openAiService.analyzeQuery(request.query());
        return ResponseEntity.ok(
            new OpenAiQueryResponse(
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
    public ResponseEntity<ErrorResponse> handleOpenAiErrors(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(ex.getMessage()));
    }

    public record OpenAiQueryRequest(String query) {
    }

    public record OpenAiQueryResponse(
        List<String> entities,
        List<String> keyConcepts,
        List<String> intents
    ) {
    }

    public record ErrorResponse(String error) {
    }
}
