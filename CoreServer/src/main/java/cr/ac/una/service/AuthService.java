package cr.ac.una.service;

import cr.ac.una.exception.AccessDeniedException;
import cr.ac.una.exception.AccessDeniedException.Reason;
import cr.ac.una.model.AuthenticatedUser;
import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.AccountChange;
import cr.ac.una.model.OperatorAccount;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.UserRole;
import cr.ac.una.repository.AccountRepository;
import cr.ac.una.repository.OperatorWindowAssignmentRepository;
import cr.ac.una.security.PasswordHasher;
import cr.ac.una.security.SessionRegistry;
import java.util.Objects;
import java.util.Optional;

// Inicia y cierra sesiones, valida tokens y aplica los permisos del servidor.
// Emisión y pantalla son públicas; atención exige OPERADOR o ADMIN con asignación propia.
// Reportes y administración exigen ADMIN; SUPERVISOR no tiene acceso habilitado.
public class AuthService {

    public record LoginResult(String token, AuthenticatedUser user) {

    }

    private static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos.";
    private static final int MAX_USERNAME_LENGTH = 50;

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final SessionRegistry sessions;
    private final OperatorWindowAssignmentRepository assignmentRepository;

    public AuthService(
            AccountRepository accountRepository,
            PasswordHasher passwordHasher,
            SessionRegistry sessions,
            OperatorWindowAssignmentRepository assignmentRepository
    ) {
        this.accountRepository = Objects.requireNonNull(
                accountRepository, "El repositorio de cuentas es obligatorio."
        );
        this.passwordHasher = Objects.requireNonNull(
                passwordHasher, "El hash de contraseñas es obligatorio."
        );
        this.sessions = Objects.requireNonNull(sessions, "El registro de sesiones es obligatorio.");
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository, "El repositorio de asignaciones es obligatorio."
        );
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Ingrese el usuario y la contraseña.");
        }
        if (username.length() > MAX_USERNAME_LENGTH || password.length() > PasswordHasher.MAX_LENGTH) {
            throw new AccessDeniedException(Reason.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }

        char[] secret = password.toCharArray();
        try {
            Optional<OperatorAccount> found = accountRepository.findByUsername(username.trim());

            // Ejecuta PBKDF2 y usa el mismo mensaje cuando falta la cuenta o su credencial.
            // Esto reduce la diferencia de respuesta entre esos casos y una clave incorrecta.
            if (found.isEmpty() || !found.get().hasCredential()) {
                passwordHasher.verifyDummy(secret);
                throw new AccessDeniedException(Reason.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
            }

            OperatorAccount account = found.get();
            if (!passwordHasher.verify(secret, account.passwordHash())) {
                throw new AccessDeniedException(Reason.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
            }

            // El estado se informa solo después de una contraseña correcta.
            requireUsable(account);

            AuthenticatedUser user = toUser(account);
            String token = sessions.create(user, PasswordHasher.fingerprint(account.passwordHash()));
            return new LoginResult(token, user);

        } finally {
            PasswordHasher.clear(secret);
        }
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    // Revoca de inmediato todas las sesiones del funcionario en este proceso.
    public void revokeSessions(int operatorId) {
        sessions.removeAllFor(operatorId);
    }

    // Valida el token y devuelve el funcionario con su rol vigente.
    public AuthenticatedUser authenticate(String token) {
        return authenticate(token, false);
    }

    // Fuerza la consulta de la cuenta y exige ADMIN antes de construir la identidad
    // administrativa.
    public AdministrationActor administrationActor(String token) {
        AuthenticatedUser user = authenticate(token, true);
        requireRole(user, UserRole.ADMIN);
        SessionRegistry.Session current = sessions.find(token).orElseThrow(() ->
                new AccessDeniedException(Reason.SESSION_EXPIRED, "La sesión terminó. Inicie sesión de nuevo."));
        return new AdministrationActor(user.operatorId(), current.credentialFingerprint());
    }

    // Revoca las sesiones de la cuenta reemplazada y conserva las de una versión posterior.
    public void accountChanged(AccountChange change) {
        sessions.removeSupersededFor(change);
    }

    private AuthenticatedUser authenticate(String token, boolean forceRevalidation) {
        if (token == null || token.isBlank()) {
            throw new AccessDeniedException(
                    Reason.UNAUTHENTICATED, "Debe iniciar sesión para esta operación."
            );
        }

        SessionRegistry.Session session = sessions.find(token).orElseThrow(()
                -> new AccessDeniedException(
                        Reason.SESSION_EXPIRED,
                        "La sesión venció o fue cerrada. Inicie sesión de nuevo."
                ));

        // Las operaciones administrativas fuerzan la consulta; las demás usan el intervalo de revalidación.
        if (!forceRevalidation && !sessions.needsRevalidation(session)) {
            sessions.touch(token);
            return session.user();
        }

        Optional<OperatorAccount> current = accountRepository.findById(session.user().operatorId());
        if (current.isEmpty() || !current.get().hasCredential()) {
            sessions.remove(token);
            throw new AccessDeniedException(
                    Reason.SESSION_EXPIRED, "La cuenta cambió. Inicie sesión de nuevo."
            );
        }

        // Revocar solo las sesiones que usaron la credencial reemplazada.
        // Una sesión con la nueva contraseña debe seguir siendo válida.
        String currentFingerprint = PasswordHasher.fingerprint(current.get().passwordHash());
        if (!currentFingerprint.equals(session.credentialFingerprint())) {
            sessions.removeSessionsWithCredential(
                    session.user().operatorId(), session.credentialFingerprint()
            );
            throw new AccessDeniedException(
                    Reason.SESSION_EXPIRED, "La contraseña de la cuenta cambió. Inicie sesión de nuevo."
            );
        }

        try {
            requireUsable(current.get());
        } catch (AccessDeniedException exception) {
            sessions.remove(token);
            throw exception;
        }

        AuthenticatedUser user = toUser(current.get());
        sessions.refresh(token, user);
        return user;
    }

    public AuthenticatedUser requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new AccessDeniedException(
                    Reason.UNAUTHENTICATED, "Debe iniciar sesión para esta operación."
            );
        }
        return user;
    }

    public void requireRole(AuthenticatedUser user, UserRole role) {
        if (requireUser(user).role() != role) {
            throw new AccessDeniedException(
                    Reason.FORBIDDEN, "Su perfil no tiene permiso para esta operación."
            );
        }
    }

    // Exige OPERADOR o ADMIN y comprueba que la asignación pertenezca a ese funcionario.
    public void requireOwnAssignment(AuthenticatedUser user, long assignmentId) {
        AuthenticatedUser current = requireUser(user);
        if (current.role() != UserRole.OPERADOR && current.role() != UserRole.ADMIN) {
            throw new AccessDeniedException(
                    Reason.FORBIDDEN, "Su perfil no tiene permiso para atender turnos."
            );
        }

        OperatorWindowAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                "No existe la asignación " + assignmentId + "."
        ));

        if (assignment.getOperatorId() != current.operatorId()) {
            throw new AccessDeniedException(
                    Reason.FORBIDDEN, "La asignación pertenece a otro funcionario."
            );
        }
    }

    private static void requireUsable(OperatorAccount account) {
        if (!account.active()) {
            throw new AccessDeniedException(Reason.ACCOUNT_DISABLED, "La cuenta está inactiva.");
        }
        if (account.role() == UserRole.SUPERVISOR) {
            throw new AccessDeniedException(
                    Reason.ACCOUNT_DISABLED, "Este perfil todavía no tiene acceso al sistema."
            );
        }
    }

    private static AuthenticatedUser toUser(OperatorAccount account) {
        return new AuthenticatedUser(
                account.operatorId(), account.username(), account.name(), account.role()
        );
    }
}
