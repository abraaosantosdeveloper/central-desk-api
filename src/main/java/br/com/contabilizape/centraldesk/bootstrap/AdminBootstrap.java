package br.com.contabilizape.centraldesk.bootstrap;

import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.model.Admin;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cria o primeiro administrador ao subir a aplicação, se ainda não existir nenhum.
 *
 * Substitui o admin que antes vinha da V8: dados pessoais e senha não ficam em migration
 * versionada no Git. Sem as variáveis ADMIN_* (como nos testes), apenas registra um aviso.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties credentials;
    private final UUID innovationDepartmentId;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrap(BootstrapAdminProperties credentials,
                          @Value("${app.tickets.handler-department-id}") UUID innovationDepartmentId,
                          AdminRepository adminRepository,
                          UserRepository userRepository,
                          DepartmentRepository departmentRepository,
                          PasswordEncoder passwordEncoder,
                          Validator validator) {
        this.credentials = credentials;
        this.innovationDepartmentId = innovationDepartmentId;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }
        if (!credentials.isComplete()) {
            log.warn("Nenhum administrador cadastrado. Defina ADMIN_NAME, ADMIN_CPF, ADMIN_EMAIL e ADMIN_PASSWORD "
                    + "para criar o primeiro na próxima inicialização.");
            return;
        }

        String email = credentials.email().trim().toLowerCase(Locale.ROOT);
        validateWithRegistrationRules(email);
        ensureNotRegistered(email);
        Department innovation = departmentRepository.findById(innovationDepartmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Departamento de Inovação não encontrado: verifique a V8 e o application.properties"));

        User user = new User(
                credentials.name().trim(),
                credentials.cpf(),
                email,
                passwordEncoder.encode(credentials.password()),
                innovation
        );
        userRepository.saveAndFlush(user);
        adminRepository.save(new Admin(user, null)); // o primeiro admin não tem quem o concedeu

        log.info("Administrador inicial criado: {}", email);
    }

    // Mesmas regras do cadastro (CPF, e-mail corporativo, força da senha...): reaproveita o DTO
    private void validateWithRegistrationRules(String email) {
        var asRegistration = new UserCreateRequest(
                credentials.name(), credentials.cpf(), email, credentials.password(), innovationDepartmentId);
        Set<ConstraintViolation<UserCreateRequest>> violations = validator.validate(asRegistration);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException("Credenciais do administrador inicial (ADMIN_*) inválidas — " + errors);
        }
    }

    private void ensureNotRegistered(String email) {
        if (userRepository.existsByEmail(email) || userRepository.existsByCpf(credentials.cpf())) {
            throw new IllegalStateException("Já existe usuário com o e-mail ou CPF de ADMIN_*. "
                    + "Para torná-lo administrador, insira o registro em admins manualmente.");
        }
    }
}
