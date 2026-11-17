package pe.edu.utp.university_system.teacher;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.auth.AccountService;
import pe.edu.utp.university_system.auth.UserAccount;
import pe.edu.utp.university_system.student.StudentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AccountService accountService;

    public List<TeacherResponse> getTeachers() {
        return teacherRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TeacherResponse getTeacherById(Long id) {
        Teacher teacher =
                teacherRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Teacher not found"));

        return toResponse(teacher);
    }

    @Transactional
    public TeacherResponse createTeacher(
            TeacherRegistrationRequest request) {

        String teacherCode = request.getTeacherCode() == null
                ? ""
                : request.getTeacherCode().trim().toUpperCase();
        String documentNumber = request.getDocumentNumber() == null
                ? ""
                : request.getDocumentNumber().trim();
        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim();
        String phone = request.getPhone() == null
                ? ""
                : request.getPhone().trim();

        if (!teacherCode.matches("C\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código de docente debe comenzar con C y tener 8 dígitos");
        }

        if (!documentNumber.matches("\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El DNI debe contener exactamente 8 dígitos");
        }

        if (!phone.matches("\\d{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El celular debe contener exactamente 9 dígitos");
        }

        if (teacherRepository.existsByTeacherCode(teacherCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código de docente ya está registrado");
        }

        if (teacherRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electrónico ya está registrado");
        }

        if (teacherRepository.existsByDocumentNumber(documentNumber)
                || studentRepository.existsByDocumentNumber(documentNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este DNI ya se encuentra registrado");
        }

        UserAccount account;
        try {
            account = accountService.createTeacherAccount(teacherCode);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }

        Teacher teacher = new Teacher();
        teacher.setFirstName(request.getFirstName());
        teacher.setLastName(request.getLastName());
        teacher.setEmail(email);
        teacher.setTeacherCode(teacherCode);
        teacher.setSpecialty(request.getSpecialty());
        teacher.setPhone(phone);
        teacher.setDocumentNumber(documentNumber);
        teacher.setHireDate(request.getHireDate());
        teacher.setAcademicDegree(request.getAcademicDegree());
        teacher.setActive(true);
        teacher.setUserAccount(account);

        Teacher saved = teacherRepository.save(teacher);

        return toResponse(saved);
    }

    @Transactional
    public TeacherResponse updateTeacher(
            Long id,
            TeacherRegistrationRequest request) {

        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El docente no existe"));

        String teacherCode = request.getTeacherCode() == null
                ? ""
                : request.getTeacherCode().trim().toUpperCase();
        String documentNumber = request.getDocumentNumber() == null
                ? ""
                : request.getDocumentNumber().trim();
        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim();
        String phone = request.getPhone() == null
                ? ""
                : request.getPhone().trim();

        if (!teacherCode.matches("C\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código de docente debe comenzar con C y tener 8 dígitos");
        }

        if (!documentNumber.matches("\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El DNI debe contener exactamente 8 dígitos");
        }

        if (!phone.matches("\\d{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El celular debe contener exactamente 9 dígitos");
        }

        if (teacherRepository.existsByTeacherCodeAndIdNot(teacherCode, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código de docente ya está registrado");
        }

        if (teacherRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electrónico ya está registrado");
        }

        if (teacherRepository.existsByDocumentNumberAndIdNot(documentNumber, id)
                || studentRepository.existsByDocumentNumber(documentNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este DNI ya se encuentra registrado");
        }

        try {
            accountService.updateTeacherAccountCode(
                    teacher.getUserAccount(),
                    teacherCode);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }

        teacher.setFirstName(request.getFirstName());
        teacher.setLastName(request.getLastName());
        teacher.setEmail(email);
        teacher.setTeacherCode(teacherCode);
        teacher.setSpecialty(request.getSpecialty());
        teacher.setPhone(phone);
        teacher.setDocumentNumber(documentNumber);
        if (request.getHireDate() != null) {
            teacher.setHireDate(request.getHireDate());
        }
        teacher.setAcademicDegree(request.getAcademicDegree());

        return toResponse(teacherRepository.save(teacher));
    }

    public void deleteTeacher(Long id) {
        teacherRepository.deleteById(id);
    }

    private TeacherResponse toResponse(Teacher teacher) {

        return new TeacherResponse(
                teacher.getId(),
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getTeacherCode(),
                teacher.getEmail(),
                teacher.getSpecialty(),
                teacher.getAcademicDegree(),
                teacher.getActive(),
                teacher.getPhone(),
                teacher.getDocumentNumber(),
                teacher.getHireDate()

        );
    }
}
