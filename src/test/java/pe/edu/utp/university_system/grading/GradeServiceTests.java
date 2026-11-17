package pe.edu.utp.university_system.grading;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import pe.edu.utp.university_system.ai.AIRecommendationService;
import pe.edu.utp.university_system.course.Evaluation;
import pe.edu.utp.university_system.course.EvaluationRepository;
import pe.edu.utp.university_system.enrollment.Enrollment;
import pe.edu.utp.university_system.enrollment.EnrollmentRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeServiceTests {

    private final GradeRepository gradeRepository = mock(GradeRepository.class);
    private final EvaluationRepository evaluationRepository = mock(EvaluationRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final AIRecommendationService recommendationService = mock(AIRecommendationService.class);
    private final GradeService service = new GradeService(
            gradeRepository,
            evaluationRepository,
            enrollmentRepository,
            recommendationService);

    @Test
    void savesGradesBeforeSchedulingAiRecommendation() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);
        Enrollment enrollment = new Enrollment();
        enrollment.setId(20L);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));
        when(enrollmentRepository.findById(20L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentIdAndEvaluationId(20L, 10L)).thenReturn(false);

        BulkGradeRegistrationRequest request = request(10L, 20L, 10.5);
        service.registerGrades(request);

        InOrder order = inOrder(gradeRepository, recommendationService);
        order.verify(gradeRepository).saveAll(anyList());
        order.verify(recommendationService).generateRecommendation(20L, 10L, 10.5);
    }

    @Test
    void doesNotScheduleAiForPassingGrade() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(10L);
        Enrollment enrollment = new Enrollment();
        enrollment.setId(20L);
        when(evaluationRepository.findById(10L)).thenReturn(Optional.of(evaluation));
        when(enrollmentRepository.findById(20L)).thenReturn(Optional.of(enrollment));
        when(gradeRepository.existsByEnrollmentIdAndEvaluationId(20L, 10L)).thenReturn(false);

        service.registerGrades(request(10L, 20L, 16.0));

        verify(gradeRepository).saveAll(anyList());
        verify(recommendationService, never()).generateRecommendation(20L, 10L, 16.0);
    }

    private BulkGradeRegistrationRequest request(Long evaluationId, Long enrollmentId, Double value) {
        StudentGradeRequest grade = new StudentGradeRequest();
        grade.setEnrollmentId(enrollmentId);
        grade.setValue(value);
        BulkGradeRegistrationRequest request = new BulkGradeRegistrationRequest();
        request.setEvaluationId(evaluationId);
        request.setGrades(List.of(grade));
        return request;
    }
}
