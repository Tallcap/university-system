package pe.edu.utp.university_system.student;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.auth.AccountService;
import pe.edu.utp.university_system.auth.UserAccount;
import pe.edu.utp.university_system.teacher.TeacherRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AccountService accountService;

    public List<StudentResponse> getStudents() {

        return studentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public StudentResponse getStudentById(
            Long id) {

        Student student =
                studentRepository.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "El estudiante no existe"));

        return toResponse(student);
    }

    @Transactional
    public StudentResponse createStudent(
            StudentRegistrationRequest request) {

        String documentNumber = request.getDocumentNumber() == null
                ? ""
                : request.getDocumentNumber().trim();

        String enrollmentCode = request.getEnrollmentCode() == null
                ? ""
                : request.getEnrollmentCode().trim().toUpperCase();

        String phone = request.getPhone() == null
                ? ""
                : request.getPhone().trim();

        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim();

        if (!enrollmentCode.matches("U\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código de estudiante debe comenzar con U y tener 8 dígitos");
        }

        if (!phone.matches("\\d{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El celular debe contener exactamente 9 dígitos");
        }

        if (request.getBirthDate() == null
                || request.getBirthDate().isAfter(LocalDate.now().minusYears(17))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estudiante debe tener al menos 17 años");
        }

        if (studentRepository.existsByEnrollmentCode(enrollmentCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código de matrícula ya está registrado");
        }

        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electrónico ya está registrado");
        }

        if (studentRepository.existsByDocumentNumber(documentNumber)
                || teacherRepository.existsByDocumentNumber(documentNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un estudiante registrado con este documento");
        }

        UserAccount account;
        try {
            account = accountService.createStudentAccount(enrollmentCode);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }

        Student student =
                new Student();

        student.setFirstName(
                request.getFirstName());

        student.setLastName(
                request.getLastName());

        student.setEmail(
                email);

        student.setEnrollmentCode(
                enrollmentCode);

        student.setPhone(
                phone);

        student.setDocumentNumber(
                documentNumber);

        student.setBirthDate(
                request.getBirthDate());

        student.setEnrollmentDate(
                LocalDate.now());

        student.setActive(true);

        student.setUserAccount(
                account);

        Student saved =
                studentRepository.save(student);

        return toResponse(saved);
    }

    @Transactional
    public StudentResponse updateStudent(
            Long id,
            StudentRegistrationRequest request) {

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El estudiante no existe"));

        String enrollmentCode = request.getEnrollmentCode() == null
                ? ""
                : request.getEnrollmentCode().trim().toUpperCase();
        String documentNumber = request.getDocumentNumber() == null
                ? ""
                : request.getDocumentNumber().trim();
        String phone = request.getPhone() == null
                ? ""
                : request.getPhone().trim();
        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim();

        if (!documentNumber.equals(student.getDocumentNumber())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El DNI del estudiante no se puede modificar");
        }

        if (request.getFirstName() == null
                || request.getLastName() == null
                || !request.getFirstName().equals(student.getFirstName())
                || !request.getLastName().equals(student.getLastName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los nombres y apellidos vinculados al DNI no se pueden modificar");
        }

        if (!enrollmentCode.matches("U\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código de estudiante debe comenzar con U y tener 8 dígitos");
        }

        if (!phone.matches("\\d{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El celular debe contener exactamente 9 dígitos");
        }

        if (request.getBirthDate() == null
                || request.getBirthDate().isAfter(LocalDate.now().minusYears(17))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estudiante debe tener al menos 17 años");
        }

        if (studentRepository.existsByEnrollmentCodeAndIdNot(enrollmentCode, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código de matrícula ya está registrado");
        }

        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El correo electrónico ya está registrado");
        }

        try {
            accountService.updateStudentAccountCode(
                    student.getUserAccount(),
                    enrollmentCode);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception);
        }

        student.setEmail(email);
        student.setEnrollmentCode(enrollmentCode);
        student.setPhone(phone);
        student.setBirthDate(request.getBirthDate());

        return toResponse(studentRepository.save(student));
    }

    public void deleteStudent(
            Long id) {

        studentRepository.deleteById(id);
    }

    private StudentResponse toResponse(
            Student student) {

        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getEnrollmentCode(),
                student.getEmail(),
                student.getActive(),
                student.getPhone(),
                student.getDocumentNumber(),
                student.getBirthDate()
        );
    }
}
