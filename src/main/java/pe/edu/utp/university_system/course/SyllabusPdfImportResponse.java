package pe.edu.utp.university_system.course;

import java.util.List;

public record SyllabusPdfImportResponse(
        List<ModuleRegistrationRequest> modules,
        double totalWeight,
        List<String> warnings) {
}
