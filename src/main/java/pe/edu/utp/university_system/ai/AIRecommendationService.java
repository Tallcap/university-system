package pe.edu.utp.university_system.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.edu.utp.university_system.course.Course;
import pe.edu.utp.university_system.course.Topic;
import pe.edu.utp.university_system.enrollment.Enrollment;
import pe.edu.utp.university_system.enrollment.EnrollmentRepository;
import pe.edu.utp.university_system.course.Evaluation;
import pe.edu.utp.university_system.course.EvaluationRepository;
import pe.edu.utp.university_system.course.Module;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AIRecommendationService {

    private final AIRecommendationRepository repository;
    private final AISettingsRepository settingsRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EvaluationRepository evaluationRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Async
    @Transactional
    public void generateRecommendation(
            Long enrollmentId,
            Long evaluationId,
            Double gradeValue) {

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la matrícula " + enrollmentId));
        Evaluation evaluation = evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la evaluación " + evaluationId));

        if (!AIRecommendationEligibility.isEligible(evaluation)) {
            return;
        }

        AISettings settings =
                settingsRepository.findById(1L)
                        .orElseThrow();

        if (!settings.getEnabled()) {
            return;
        }

        if (gradeValue >=
                settings.getMinimumGradeThreshold()) {
            return;
        }

        List<String> topics =
                getTopicsForReinforcement(
                        evaluation
                );

        String prompt =
                settings.getSystemPrompt()
                        .replace("{course}",
                                evaluation
                                        .getModule()
                                        .getCourse()
                                        .getName())
                        .replace("{grade}",
                                String.valueOf(
                                        gradeValue))
                        .replace("{evaluation}",
                                evaluation
                                        .getTitle())
                        .replace("{topics}",
                                String.join(
                                        ", ",
                                        topics));

        AIRecommendation recommendation = new AIRecommendation();
        recommendation.setEnrollment(enrollment);
        recommendation.setGradeObtained(gradeValue);
        recommendation.setEvaluationTitle(evaluation.getTitle());
        recommendation.setModelName(settings.getModelName());
        recommendation.setPrompt(prompt);
        recommendation.setReadByStudent(false);
        recommendation.setGeneratedAt(LocalDateTime.now());

        // integracion gemini
        try {
            GeminiResult aiResult = callGeminiApi(
                    prompt,
                    settings.getModelName(),
                    settings.getTemperature(),
                    settings.getMaxOutputTokens());

            recommendation.setRecommendationContent(aiResult.text());
            recommendation.setModelName(aiResult.modelName());
            recommendation.setStatus(RecommendationStatus.PENDING_REVIEW);
        }catch (Exception e) {
            e.printStackTrace();
            recommendation.setRecommendationContent(getSafeGeminiError(e));
            recommendation.setStatus(RecommendationStatus.FAILED);
        }

        repository.save(recommendation);
    }

    public void validateRetry(Long recommendationId) {
        AIRecommendation recommendation = repository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La recomendación no existe"));

        if (!canRegenerate(recommendation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden regenerar recomendaciones rechazadas o fallidas");
        }

        AISettings settings = settingsRepository.findById(1L)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No existe la configuración de IA"));
        if (!settings.getEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La generación por IA está desactivada");
        }
    }

    @Async
    @Transactional
    public void retryFailedRecommendation(Long recommendationId) {
        AIRecommendation recommendation = repository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La recomendación no existe"));

        if (!canRegenerate(recommendation.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden regenerar recomendaciones rechazadas o fallidas");
        }

        AISettings settings = settingsRepository.findById(1L)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No existe la configuración de IA"));

        if (!settings.getEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La generación por IA está desactivada");
        }

        try {
            GeminiResult aiResult = callGeminiApi(
                    recommendation.getPrompt(),
                    settings.getModelName(),
                    settings.getTemperature(),
                    settings.getMaxOutputTokens());

            if (aiResult.text() == null || aiResult.text().isBlank()) {
                throw new IllegalStateException("Gemini devolvió una respuesta vacía");
            }

            recommendation.setRecommendationContent(aiResult.text());
            recommendation.setModelName(aiResult.modelName());
            recommendation.setStatus(RecommendationStatus.PENDING_REVIEW);
            recommendation.setGeneratedAt(LocalDateTime.now());
            repository.save(recommendation);
        } catch (Exception exception) {
            String message = getSafeGeminiError(exception);
            recommendation.setRecommendationContent(message);
            recommendation.setStatus(RecommendationStatus.FAILED);
            recommendation.setGeneratedAt(LocalDateTime.now());
            repository.save(recommendation);
        }
    }

    private boolean canRegenerate(RecommendationStatus status) {
        return status == RecommendationStatus.FAILED
                || status == RecommendationStatus.REJECTED;
    }

    private String getSafeGeminiError(Exception exception) {
        if (exception instanceof RestClientResponseException httpException) {
            int status = httpException.getStatusCode().value();
            return switch (status) {
                case 401 -> "Gemini rechazó la clave API: la clave es inválida o expiró (401)";
                case 403 -> "Gemini rechazó los permisos de la clave o del proyecto (403)";
                case 404 -> "El modelo configurado no está disponible en Gemini (404)";
                case 429 -> "Gemini alcanzó el límite de solicitudes o cuota disponible (429)";
                case 503 -> "Gemini está temporalmente saturado o fuera de servicio (503)";
                default -> "Gemini rechazó la solicitud (HTTP " + status + ")";
            };
        }
        return "No fue posible conectar con Gemini: " + exception.getMessage();
    }

    private GeminiResult callGeminiApi(String prompt, String modelName, Double temperature,
                                       Integer maxOutputTokens) throws Exception {
        try {
            return new GeminiResult(
                    callGeminiApiOnce(prompt, modelName, temperature, maxOutputTokens),
                    modelName);
        } catch (Exception primaryException) {
            if (!isTemporaryGeminiFailure(primaryException)) {
                throw primaryException;
            }

            String fallbackModel = "gemini-3.1-flash-lite";
            if (fallbackModel.equals(modelName)) {
                fallbackModel = "gemini-3.8-flash";
            }

            try {
                Thread.sleep(2000);
                return new GeminiResult(
                        callGeminiApiOnce(prompt, fallbackModel, temperature, maxOutputTokens),
                        fallbackModel);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw interruptedException;
            } catch (Exception fallbackException) {
                fallbackException.addSuppressed(primaryException);
                throw fallbackException;
            }
        }
    }

    private boolean isTemporaryGeminiFailure(Exception exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 500 || status == 502 || status == 503 || status == 504;
        }
        return false;
    }

    private String callGeminiApiOnce(String prompt, String modelName, Double temperature,
                                     Integer maxOutputTokens) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY no está definida en el proceso que ejecuta la aplicación");
        }
        if (modelName == null || !modelName.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("El identificador del modelo Gemini no es válido");
        }

        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName + ":generateContent";

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        ObjectMapper mapper = new ObjectMapper();
        var requestNode = mapper.createObjectNode();
        var contents = requestNode.putArray("contents");
        var userContent = contents.addObject();
        userContent.put("role", "user");
        userContent.putArray("parts").addObject().put("text", prompt);

        var generationConfig = requestNode.putObject("generationConfig");
        generationConfig
                .put("temperature", temperature)
                .put("maxOutputTokens", maxOutputTokens);
        generationConfig.putObject("thinkingConfig")
                .put("thinkingLevel", "low");
        String requestBody = mapper.writeValueAsString(requestNode);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

        JsonNode rootNode = mapper.readTree(response.getBody());
        StringBuilder generatedText = new StringBuilder();
        for (JsonNode candidate : rootNode.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.hasNonNull("text")) {
                    if (!generatedText.isEmpty()) {
                        generatedText.append(System.lineSeparator());
                    }
                    generatedText.append(part.path("text").asText());
                }
            }
        }

        if (generatedText.isEmpty()) {
            String blockReason = rootNode.path("promptFeedback").path("blockReason").asText();
            if (!blockReason.isBlank()) {
                throw new IllegalStateException(
                        "Gemini bloqueó la solicitud: " + blockReason);
            }
            throw new IllegalStateException("Gemini devolvió una respuesta sin texto");
        }
        return generatedText.toString();
    }

    private record GeminiResult(String text, String modelName) {
    }

    private List<String> getTopicsForReinforcement(Evaluation evaluation) {
        List<String> topics = new ArrayList<>();

        int currentModule = evaluation.getModule().getModuleNumber();
        Course course = evaluation.getModule().getCourse();

        int previousEvalModule = 0;

        // Encontrar dinámicamente en qué módulo fue la evaluación anterior
        for (Module module : course.getModules()) {
            if (module.getModuleNumber() < currentModule && !module.getEvaluations().isEmpty()) {
                if (module.getModuleNumber() > previousEvalModule) {
                    previousEvalModule = module.getModuleNumber();
                }
            }
        }

        // Recolecta los temas estrictamente en ese rango
        for (Module module : course.getModules()) {
            if (module.getModuleNumber() > previousEvalModule && module.getModuleNumber() <= currentModule) {
                for (Topic topic : module.getTopics()) {
                    topics.add(topic.getName());
                }
            }
        }

        return topics;
    }
}
