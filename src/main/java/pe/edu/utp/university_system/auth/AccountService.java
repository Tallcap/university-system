package pe.edu.utp.university_system.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserAccount createStudentAccount(
            String studentCode) {

        UserAccount existingAccount = userAccountRepository
                .findByUsername(studentCode)
                .orElse(null);

        if (existingAccount != null) {
            if (existingAccount.getRole() == Role.STUDENT) {
                return existingAccount;
            }
            throw new IllegalStateException("El código ya pertenece a otro tipo de usuario");
        }

        UserAccount account = new UserAccount();
        account.setUsername(studentCode);
        account.setPassword(passwordEncoder.encode(studentCode));
        account.setRole(Role.STUDENT);
        account.setActive(true);

        return userAccountRepository.save(account);
    }

    public UserAccount createTeacherAccount(
            String teacherCode) {

        UserAccount existingAccount = userAccountRepository
                .findByUsername(teacherCode)
                .orElse(null);

        if (existingAccount != null) {
            if (existingAccount.getRole() == Role.TEACHER) {
                return existingAccount;
            }
            throw new IllegalStateException("El código ya pertenece a otro tipo de usuario");
        }

        UserAccount account = new UserAccount();
        account.setUsername(teacherCode);
        account.setPassword(passwordEncoder.encode(teacherCode));
        account.setRole(Role.TEACHER);
        account.setActive(true);

        return userAccountRepository.save(account);
    }

    public UserAccount updateStudentAccountCode(
            UserAccount account,
            String studentCode) {

        if (account.getUsername().equalsIgnoreCase(studentCode)) {
            return account;
        }

        UserAccount existingAccount = userAccountRepository
                .findByUsername(studentCode)
                .orElse(null);

        if (existingAccount != null && !existingAccount.getId().equals(account.getId())) {
            throw new IllegalStateException("El código ya pertenece a otro usuario");
        }

        account.setUsername(studentCode);
        account.setPassword(passwordEncoder.encode(studentCode));
        return userAccountRepository.save(account);
    }

    public UserAccount updateTeacherAccountCode(
            UserAccount account,
            String teacherCode) {

        if (account.getUsername().equalsIgnoreCase(teacherCode)) {
            return account;
        }

        UserAccount existingAccount = userAccountRepository
                .findByUsername(teacherCode)
                .orElse(null);

        if (existingAccount != null && !existingAccount.getId().equals(account.getId())) {
            throw new IllegalStateException("El código ya pertenece a otro usuario");
        }

        account.setUsername(teacherCode);
        account.setPassword(passwordEncoder.encode(teacherCode));
        return userAccountRepository.save(account);
    }

    public UserAccount createAdminAccount(
            String username,
            String password) {

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setRole(Role.ADMIN);
        account.setActive(true);

        return userAccountRepository.save(account);
    }
}
