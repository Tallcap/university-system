package pe.edu.utp.university_system.teacher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher,Long> {
    Optional<Teacher> findByTeacherCode(String teacherCode);
    Optional<Teacher> findByUserAccountId(Long userAccountId);
    boolean existsByTeacherCode(String teacherCode);
    boolean existsByTeacherCodeAndIdNot(String teacherCode, Long id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    boolean existsByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumberAndIdNot(String documentNumber, Long id);
}
