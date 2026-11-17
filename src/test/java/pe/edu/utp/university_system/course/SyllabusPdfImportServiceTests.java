package pe.edu.utp.university_system.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyllabusPdfImportServiceTests {

    private final SyllabusPdfImportService service = new SyllabusPdfImportService();

    @Test
    void importsUtpTopicsAndFiveEvaluationTypes() {
        String text = mathUnits() + """
                7. SISTEMA DE EVALUACIÓN
                (20%)PC1 + (25%)EXPA + (20%)PC2 + (10%)PA + (25%)EXFN
                Tipo Descripción Semana Observación
                PC1 PRÁCTICA CALIFICADA 1 6 Individual
                EXPA EXAMEN PARCIAL 10 Individual
                PC2 PRÁCTICA CALIFICADA 2 15 Individual
                PA PARTICIPACIÓN EN CLASE 17 Grupal
                EXFN EXAMEN FINAL 18 Individual
                Indicaciones sobre Fórmulas de Evaluación:
                10. CRONOGRAMA DE ACTIVIDADES
                """;

        SyllabusPdfImportResponse response = service.parseSyllabus(text);

        assertThat(response.modules()).hasSize(18);
        assertThat(response.totalWeight()).isEqualTo(100.0);
        assertThat(response.modules().get(0).getTopics()).containsExactly("Tema 1.");
        assertThat(response.modules().get(5).getTopics())
                .containsExactly("Tema 1.", "Tema 2.", "Tema 3.", "Tema 4.", "Tema 5.");
        assertThat(response.modules().get(9).getTopics())
                .containsExactly("Tema 6.", "Tema 7.", "Tema 8.");
        assertThat(response.modules().get(14).getTopics())
                .containsExactly("Tema 9.", "Tema 10.", "Tema 11.", "Tema 12.");
        assertThat(response.modules().get(16).getTopics())
                .containsExactly("Tema 13.", "Tema 14.");
        assertThat(response.modules().get(17).getTopics())
                .containsExactly("Tema 13.", "Tema 14.");
        assertThat(response.modules().get(16).getEvaluations().getFirst().getEvaluationType())
                .isEqualTo(EvaluationType.CLASS_PARTICIPATION);
    }

    @Test
    void importsSyllabusWithoutClassParticipation() {
        String text = mathUnits() + """
                7. SISTEMA DE EVALUACIÓN
                (20%)PC1 + (30%)EXPA + (20%)PC2 + (30%)EXFN
                Tipo Descripción Semana Observación
                PC1 PRÁCTICA CALIFICADA 1 6 Individual
                EXPA EXAMEN PARCIAL 10 Individual
                PC2 PRÁCTICA CALIFICADA 2 15 Individual
                EXFN EXAMEN FINAL 18 Individual
                Indicaciones sobre Fórmulas de Evaluación:
                10. CRONOGRAMA DE ACTIVIDADES
                """;

        SyllabusPdfImportResponse response = service.parseSyllabus(text);

        assertThat(response.totalWeight()).isEqualTo(100.0);
        assertThat(response.modules().get(16).getTopics()).containsExactly("Tema 14.");
        assertThat(response.modules().get(16).getEvaluations()).isEmpty();
        assertThat(response.modules().stream()
                .flatMap(module -> module.getEvaluations().stream()))
                .extracting(EvaluationRegistrationRequest::getEvaluationType)
                .doesNotContain(EvaluationType.CLASS_PARTICIPATION);
    }

    @Test
    void importsArbitraryEvaluationCodesAndTopicsOnEvaluationWeeks() {
        String text = """
                Unidad de aprendizaje 1: Semana 1,2,3,4 y 5
                Temario:
                Tema 1.
                Tema 2.
                Tema 3.
                Tema 4.
                Tema 5.
                Unidad de aprendizaje 2: Semana 6,7,8,9 y 10
                Temario:
                Tema 6.
                Sesión integradora 1
                Tema 8.
                Tema 9.
                Tema 10.
                Unidad de aprendizaje 3: Semana 11,12,13 y 14
                Temario:
                Tema 11.
                Tema 12.
                Tema 13.
                Sesión integradora 2
                Unidad de aprendizaje 4: Semana 15,16,17 y 18
                Temario:
                Tema 15.
                Tema 16.
                Tema 17.
                6. METODOLOGÍA
                7. SISTEMA DE EVALUACIÓN
                (15%)PC + (15%)TA + (20%)AP + (20%)PA + (30%)PTF
                Tipo Descripción Semana Observación
                PC PRÁCTICA CALIFICADA 5 Evaluación individual
                TA TAREA ACADÉMICA 10 Evaluación individual
                AP AVANCE DE PORTAFOLIO 15 Evaluación grupal
                PA PARTICIPACIÓN EN CLASE 17 Evaluación individual
                PTF PORTAFOLIO FINAL 18 Evaluación grupal
                Indicaciones sobre Fórmulas de Evaluación:
                10. CRONOGRAMA DE ACTIVIDADES
                """;

        SyllabusPdfImportResponse response = service.parseSyllabus(text);

        assertThat(response.totalWeight()).isEqualTo(100.0);
        assertThat(response.modules().get(4).getTopics())
                .containsExactly("Tema 1.", "Tema 2.", "Tema 3.", "Tema 4.", "Tema 5.");
        assertThat(response.modules().get(9).getTopics())
                .containsExactly("Tema 6.", "Sesión integradora 1", "Tema 8.", "Tema 9.", "Tema 10.");
        assertThat(response.modules().get(14).getTopics())
                .containsExactly("Tema 11.", "Tema 12.", "Tema 13.", "Sesión integradora 2", "Tema 15.");
        assertThat(response.modules().get(17).getTopics())
                .containsExactly("Tema 16.", "Tema 17.");
        assertThat(response.modules().get(9).getEvaluations().getFirst().getEvaluationType())
                .isEqualTo(EvaluationType.ACADEMIC_TASK);
        assertThat(response.modules().get(9).getEvaluations().getFirst().getAiFeedbackEnabled()).isTrue();
        assertThat(response.modules().get(14).getEvaluations().getFirst().getEvaluationType())
                .isEqualTo(EvaluationType.PORTFOLIO_PROJECT_ADVANCE);
        assertThat(response.modules().get(14).getEvaluations().getFirst().getAiFeedbackEnabled()).isFalse();
        assertThat(response.modules().get(17).getEvaluations().getFirst().getEvaluationType())
                .isEqualTo(EvaluationType.FINAL_PORTFOLIO_PROJECT);
        assertThat(response.modules().get(17).getEvaluations().getFirst().getAiFeedbackEnabled()).isFalse();
        assertThat(response.modules().get(17).getEvaluations().getFirst().getTitle())
                .isEqualTo("Portafolio final");
    }

    @Test
    void distributesMultipleSessionsPerWeekWithoutLosingTopics() {
        String text = """
                Unidad de aprendizaje 1: Semana 1,2 y 3
                Temario:
                Tema 1
                Tema 2
                Tema 3
                Tema 4
                Tema 5
                Unidad de aprendizaje 2: Semana 4,5,6 y 7
                Temario:
                Tema 6
                Tema 7
                Tema 8
                Tema 9
                Tema 10
                Tema 11
                Unidad de aprendizaje 3: Semana 8,9,10 y 11
                Temario:
                Tema 12
                Tema 13
                Tema 14
                Tema 15
                Tema 16
                Tema 17
                Unidad de aprendizaje 4: Semana 12,13,14,15,16,17 y 18
                Temario:
                Tema 18
                Tema 19
                Tema 20
                Tema 21
                Tema 22
                Tema 23
                Tema 24
                Tema 25
                Tema 26
                Tema 27
                6. METODOLOGIA
                7. SISTEMA DE EVALUACION
                (10%)TA1 + (20%)PC1 + (20%)TA2 + (20%)PC2 + (30%)EXFN
                Tipo Descripcion Semana Observacion
                TA1 TAREA ACADEMICA 1 3 Individual
                PC1 PRACTICA CALIFICADA 1 7 Individual
                TA2 TAREA ACADEMICA 2 11 Individual
                PC2 PRACTICA CALIFICADA 2 15 Individual
                EXFN EXAMEN FINAL 18 Individual
                Indicaciones sobre Formulas de Evaluacion:
                10. CRONOGRAMA DE ACTIVIDADES
                """;
        StringBuilder schedule = new StringBuilder(text);
        for (int session = 1; session <= 35; session++) {
            schedule.append('\n').append(session).append('\n').append("Actividad");
        }

        SyllabusPdfImportResponse response = service.parseSyllabus(schedule.toString());

        assertThat(response.modules().get(0).getTopics()).containsExactly("Tema 1", "Tema 2");
        assertThat(response.modules().get(1).getTopics()).containsExactly("Tema 3", "Tema 4");
        assertThat(response.modules().get(2).getTopics()).containsExactly(
                "Tema 1", "Tema 2", "Tema 3", "Tema 4", "Tema 5");
        assertThat(response.modules().get(6).getTopics()).hasSize(6);
        assertThat(response.modules().get(10).getTopics()).hasSize(6);
        assertThat(response.modules().get(14).getTopics()).hasSize(6);
        assertThat(response.modules().get(17).getTopics()).containsExactly(
                "Tema 24", "Tema 25", "Tema 26", "Tema 27");
    }

    private String mathUnits() {
        return """
                5. UNIDADES Y LOGROS ESPECÍFICOS DE APRENDIZAJE
                Unidad de aprendizaje 1: Semana 1,2 y 3
                Temario:
                Tema 1.
                Tema 2.
                Tema 3.
                Unidad de aprendizaje 2: Semana 4,5 y 6
                Temario:
                Tema 4.
                Tema 5.
                Unidad de aprendizaje 3: Semana 7,8,9 y 10
                Temario:
                Tema 6.
                Tema 7.
                Tema 8.
                Unidad de aprendizaje 4: Semana 11,12,13,14,15,16,17 y 18
                Temario:
                Tema 9.
                Tema 10.
                Tema 11.
                Tema 12.
                Tema 13.
                Tema 14.
                6. METODOLOGÍA
                """;
    }
}
