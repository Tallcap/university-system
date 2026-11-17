package pe.edu.utp.university_system.student;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.teacher.TeacherRepository;

@Service
@RequiredArgsConstructor
public class DniLookupService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @Value("${apiinti.base-url}")
    private String baseUrl;

    @Value("${apiinti.token}")
    private String token;

    public DniLookupResponse lookup(String rawDni) {
        String dni = rawDni == null ? "" : rawDni.trim();

        if (!dni.matches("\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El DNI debe contener exactamente 8 dígitos");
        }

        if (studentRepository.existsByDocumentNumber(dni)
                || teacherRepository.existsByDocumentNumber(dni)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este DNI ya se encuentra registrado");
        }

        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La consulta DNI no está configurada. Define APIINTI_TOKEN");
        }

        try {
            ApiIntiResponse response = RestClient.builder()
                    .baseUrl(baseUrl)
                    .build()
                    .get()
                    .uri("/dni/{dni}", dni)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(ApiIntiResponse.class);

            ApiIntiData data = response == null ? null : response.data();
            String returnedDni = data == null ? "" : clean(data.dni());
            String firstName = data == null ? "" : clean(data.nombres());
            String paternalLastName = data == null ? "" : clean(data.apellidoPaterno());
            String maternalLastName = data == null ? "" : clean(data.apellidoMaterno());
            String lastName = (paternalLastName + " " + maternalLastName).trim();

            if (response == null || !response.success()
                    || !dni.equals(returnedDni)
                    || firstName.isBlank()
                    || lastName.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "API Inti devolvió una respuesta DNI incompleta");
            }

            return new DniLookupResponse(returnedDni, firstName, lastName);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontraron datos para este DNI",
                        exception);
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No fue posible consultar API Inti",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "API Inti no está disponible en este momento",
                    exception);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record ApiIntiResponse(boolean success, ApiIntiData data) {
    }

    private record ApiIntiData(
            String dni,
            String nombres,
            String apellidoPaterno,
            String apellidoMaterno) {
    }
}
