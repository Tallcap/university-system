package pe.edu.utp.university_system.ai;

import pe.edu.utp.university_system.course.Evaluation;
import pe.edu.utp.university_system.course.EvaluationType;

import java.text.Normalizer;
import java.util.Locale;

public final class AIRecommendationEligibility {

    private AIRecommendationEligibility() {
    }

    public static boolean isEligible(Evaluation evaluation) {
        if (evaluation == null) return false;
        return Boolean.TRUE.equals(evaluation.getAiFeedbackEnabled());
    }

    public static boolean defaultEnabled(EvaluationType type, String evaluationTitle) {
        if (type == EvaluationType.CLASS_PARTICIPATION
                || type == EvaluationType.FINAL_EXAM
                || type == EvaluationType.PORTFOLIO_PROJECT_ADVANCE
                || type == EvaluationType.FINAL_PORTFOLIO_PROJECT) {
            return false;
        }
        String title = normalize(evaluationTitle);
        if (title.contains("PARTICIPACION") || title.contains("PORTAFOLIO")) {
            return false;
        }
        if (title.contains("EXAMEN FINAL")) {
            return false;
        }
        return !(title.contains("PROYECTO") && title.contains("FINAL"));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }
}
