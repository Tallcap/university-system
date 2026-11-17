package pe.edu.utp.university_system.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
public class AISettingsController {

    private final AISettingsService service;
    private final AIRecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<AISettingsResponse>
    getSettings() {

        return ResponseEntity.ok(
                service.getSettings()
        );
    }

    @PutMapping
    public ResponseEntity<AISettingsResponse>
    updateSettings(
            @RequestBody AISettingsRequest request) {

        return ResponseEntity.ok(
                service.updateSettings(request)
        );
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AILogResponse>> getLogs() {
        return ResponseEntity.ok(service.getGenerationHistory());
    }

    @PostMapping("/logs/{id}/retry")
    public ResponseEntity<Map<String, String>> retryGeneration(@PathVariable Long id) {
        recommendationService.validateRetry(id);
        recommendationService.retryFailedRecommendation(id);
        return ResponseEntity.accepted().body(Map.of(
                "message",
                "Regeneración iniciada en segundo plano. Puedes continuar usando el sistema."));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleAIError(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason()));
    }
}
