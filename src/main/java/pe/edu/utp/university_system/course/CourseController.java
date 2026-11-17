package pe.edu.utp.university_system.course;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final SyllabusPdfImportService syllabusPdfImportService;

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(
            @RequestBody CourseRegistrationRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(courseService.createCourse(request));
    }

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourse(id));
    }

    @GetMapping("/{id}/details")
    public ResponseEntity<CourseDetailsResponse> getCourseDetails(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseDetails(id));
    }

    @PostMapping(value = "/syllabus/import-pdf", consumes = "multipart/form-data")
    public ResponseEntity<SyllabusPdfImportResponse> importSyllabusPdf(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(syllabusPdfImportService.importPdf(file));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleCourseError(
            org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason()));
    }
}
