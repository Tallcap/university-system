package pe.edu.utp.university_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UniversitySystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniversitySystemApplication.class, args);
	}

}
