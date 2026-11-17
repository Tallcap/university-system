package pe.edu.utp.university_system.student;

public record DniLookupResponse(
        String documentNumber,
        String firstName,
        String lastName) {
}
