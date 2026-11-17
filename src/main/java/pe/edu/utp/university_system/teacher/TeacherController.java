package pe.edu.utp.university_system.teacher;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.university_system.student.DniLookupResponse;
import pe.edu.utp.university_system.student.DniLookupService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final DniLookupService dniLookupService;

    @GetMapping
    public ResponseEntity<List<TeacherResponse>> getTeachers() {
        return ResponseEntity.ok(teacherService.getTeachers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherResponse> getTeacher(@PathVariable Long id) {
        return ResponseEntity.ok(teacherService.getTeacherById(id));
    }

    @GetMapping("/lookup-dni/{dni}")
    public ResponseEntity<DniLookupResponse> lookupDni(@PathVariable String dni) {
        return ResponseEntity.ok(dniLookupService.lookup(dni));
    }

    @PostMapping
    public ResponseEntity<TeacherResponse> createTeacher(@RequestBody TeacherRegistrationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(teacherService.createTeacher(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherResponse> updateTeacher(
            @PathVariable Long id,
            @RequestBody TeacherRegistrationRequest request) {
        return ResponseEntity.ok(teacherService.updateTeacher(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeacher(@PathVariable Long id) {
        teacherService.deleteTeacher(id);
        return ResponseEntity
                .noContent()
                .build();
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleTeacherError(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason()));
    }
}
