package pe.edu.utp.university_system.ai;

import org.junit.jupiter.api.Test;
import pe.edu.utp.university_system.course.Evaluation;
import pe.edu.utp.university_system.course.EvaluationType;

import static org.assertj.core.api.Assertions.assertThat;

class AIRecommendationEligibilityTests {

    @Test
    void defaultsToDisabledForParticipationPortfoliosFinalProjectsAndFinalExams() {
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.CLASS_PARTICIPATION, "Participación en clase")).isFalse();
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.PORTFOLIO_PROJECT_ADVANCE, "Avance de portafolio")).isFalse();
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.FINAL_PORTFOLIO_PROJECT, "Proyecto integrador final")).isFalse();
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.FINAL_EXAM, "Examen Final")).isFalse();
    }

    @Test
    void allowsPracticesTasksAndPartialExams() {
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.PC_1, "Práctica Calificada")).isTrue();
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.ACADEMIC_TASK, "Tarea Académica")).isTrue();
        assertThat(AIRecommendationEligibility.defaultEnabled(
                EvaluationType.PARCIAL_EXAM, "Examen Parcial")).isTrue();
    }

    @Test
    void switchIsTheFinalSourceOfTruth() {
        assertThat(isEligible("Portafolio final", EvaluationType.FINAL_PORTFOLIO_PROJECT, true)).isTrue();
        assertThat(isEligible("Práctica Calificada", EvaluationType.PC_1, false)).isFalse();
    }

    private boolean isEligible(String title, EvaluationType type, boolean enabled) {
        Evaluation evaluation = new Evaluation();
        evaluation.setTitle(title);
        evaluation.setEvaluationType(type);
        evaluation.setAiFeedbackEnabled(enabled);
        return AIRecommendationEligibility.isEligible(evaluation);
    }
}
