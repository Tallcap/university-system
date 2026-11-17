package pe.edu.utp.university_system.grading;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.ai.AIRecommendationService;
import pe.edu.utp.university_system.course.Evaluation;
import pe.edu.utp.university_system.course.EvaluationRepository;
import pe.edu.utp.university_system.enrollment.Enrollment;
import pe.edu.utp.university_system.enrollment.EnrollmentRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private static final double PASSING_GRADE = 11.5;

    private final GradeRepository gradeRepository;
    private final EvaluationRepository evaluationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AIRecommendationService aiRecommendationService;

    public void registerGrades(
            BulkGradeRegistrationRequest request) {

        Evaluation evaluation = evaluationRepository.findById(
                        request.getEvaluationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La evaluación seleccionada no existe"));

        List<Grade> gradesToSave = new ArrayList<>();

        for (StudentGradeRequest gradeRequest :
                request.getGrades()) {

            Enrollment enrollment =
                    enrollmentRepository.findById(
                                    gradeRequest.getEnrollmentId())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "No existe la matrícula " + gradeRequest.getEnrollmentId()));

            if (gradeRequest.getValue() == null
                    || gradeRequest.getValue() < 0
                    || gradeRequest.getValue() > 20) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La calificación debe estar entre 0 y 20");
            }

            if (gradeRepository
                    .existsByEnrollmentIdAndEvaluationId(
                            enrollment.getId(),
                            evaluation.getId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "La calificación de este estudiante ya fue registrada para esta evaluación");
            }

            Grade grade = new Grade();
            grade.setValue(gradeRequest.getValue());
            grade.setEnrollment(enrollment);
            grade.setEvaluation(evaluation);
            gradesToSave.add(grade);
        }

        gradeRepository.saveAll(gradesToSave);

        // Primero se confirman las notas. Gemini trabaja después en segundo
        // plano para que una respuesta lenta no bloquee al docente.
        for (Grade grade : gradesToSave) {
            if (grade.getValue() < PASSING_GRADE) {
                aiRecommendationService.generateRecommendation(
                        grade.getEnrollment().getId(),
                        evaluation.getId(),
                        grade.getValue());
            }
        }
    }

    public Double calculateCurrentAverage(
            Long enrollmentId) {

        List<Grade> grades =
                gradeRepository.findByEnrollmentId(
                        enrollmentId);

        double weightedAverage = 0.0;

        for (Grade grade : grades) {

            double weight =
                    grade.getEvaluation().getWeight()
                            / 100.0;

            weightedAverage +=
                    grade.getValue() * weight;
        }

        return Math.round(
                weightedAverage * 100.0)
                / 100.0;
    }

    public List<GradeResponse> getGradesByEvaluation(Long evaluationId) {
        return gradeRepository.findByEvaluationId(evaluationId)
                .stream()
                .map(grade -> new GradeResponse(
                        grade.getId(),
                        grade.getEnrollment().getId(),
                        grade.getEvaluation().getId(),
                        grade.getValue()
                ))
                .toList();
    }

}
