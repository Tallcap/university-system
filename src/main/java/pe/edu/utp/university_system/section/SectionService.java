package pe.edu.utp.university_system.section;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.course.Course;
import pe.edu.utp.university_system.course.CourseRepository;
import pe.edu.utp.university_system.teacher.Teacher;
import pe.edu.utp.university_system.teacher.TeacherRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;

    public SectionResponse createSection(SectionRegistrationRequest request) {

        String sectionName = request.getSectionName() == null
                ? ""
                : request.getSectionName().trim();

        if (!sectionName.matches("\\d{5}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código de sección debe contener exactamente 5 dígitos");
        }

        if (sectionRepository.existsBySectionName(sectionName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código de sección ya está registrado");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Course not found"));

        Teacher teacher = teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        Section section = new Section();
        section.setSectionName(sectionName);
        section.setCourse(course);
        section.setTeacher(teacher);

        Section saved = sectionRepository.save(section);

        return new SectionResponse(
                saved.getId(),
                saved.getSectionName(),
                saved.getCourse().getId(),
                saved.getTeacher().getId()
        );
    }

    public SectionResponse getSection(Long id) {

        Section section = sectionRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Section not found"));

        return new SectionResponse(
                section.getId(),
                section.getSectionName(),
                section.getCourse().getId(),
                section.getTeacher().getId()
        );
    }
    
    public List<SectionResponse> getSections() {
        return sectionRepository.findAll()
                .stream()
                .map(section -> new SectionResponse(
                        section.getId(),
                        section.getSectionName(),
                        section.getCourse().getId(),
                        section.getTeacher().getId()
                ))
                .toList();
    }

    public List<SectionResponse> getSectionsByCourse(Long courseId) {

        return sectionRepository.findByCourseId(courseId)
                .stream()
                .map(section -> new SectionResponse(
                        section.getId(),
                        section.getSectionName(),
                        section.getCourse().getId(),
                        section.getTeacher().getId()
                ))
                .toList();
    }

    public List<SectionResponse> getSectionsByTeacherId(Long teacherId) {

        return sectionRepository.findByTeacherId(teacherId)
                .stream()
                .map(section -> new SectionResponse(
                        section.getId(),
                        section.getSectionName(),
                        section.getCourse().getId(),
                        section.getTeacher().getId()
                ))
                .toList();
    }
}
