package cr.ac.una.security;

import cr.ac.una.model.AuthenticatedUser;
import cr.ac.una.model.OperatorAccount;
import cr.ac.una.model.AccountChange;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Conserva las sesiones en memoria compartida por los hilos de CoreServer.
// ConcurrentHashMap reemplaza cada entrada inmutable de forma atómica.
// Reiniciar el servidor termina todas las sesiones registradas.
public final class SessionRegistry {

    // Sin uso durante este tiempo, la sesión vence.
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    // Aunque se use, la sesión no dura más que esto.
    public static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);
    // Cada cuánto se vuelve a leer el estado y el rol del funcionario.
    public static final Duration REVALIDATION_INTERVAL = Duration.ofSeconds(60);

    private static final int TOKEN_BYTES = 32;

    // credentialFingerprint identifica la contraseña con que se inició la
    // sesión (huella del hash guardado). Si la contraseña cambia, la huella
    // deja de coincidir y la sesión se revoca. Nunca sale del servidor.
    public record Session(
            AuthenticatedUser user,
            String credentialFingerprint,
            Instant createdAt,
            Instant lastUsedAt,
            Instant verifiedAt
            ) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public SessionRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "El reloj es obligatorio.");
    }

    public String create(AuthenticatedUser user, String credentialFingerprint) {
        Objects.requireNonNull(user, "El usuario es obligatorio.");
        Objects.requireNonNull(credentialFingerprint, "La huella de la credencial es obligatoria.");
        purgeExpired();

        Instant now = clock.instant();
        Session session = new Session(user, credentialFingerprint, now, now, now);

        while (true) {
            String token = newToken();
            if (sessions.putIfAbsent(token, session) == null) {
                return token;
            }
        }
    }

    // Consulta la sesión y la elimina si ya superó su tiempo de vida o de inactividad.
    public Optional<Session> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }

        if (isExpired(session, clock.instant())) {
            sessions.remove(token, session);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    // La sesión puede reutilizar su identidad hasta que llegue el intervalo de consulta de la cuenta.
    public boolean needsRevalidation(Session session) {
        return !clock.instant().isBefore(session.verifiedAt().plus(REVALIDATION_INTERVAL));
    }

    // Registra el uso de la sesión.
    public void touch(String token) {
        Instant now = clock.instant();
        sessions.computeIfPresent(token, (key, current)
                -> new Session(current.user(), current.credentialFingerprint(),
                        current.createdAt(), now, current.verifiedAt()));
    }

    // Registra el uso y los datos del funcionario recién leídos de la base.
    public void refresh(String token, AuthenticatedUser user) {
        Instant now = clock.instant();
        sessions.computeIfPresent(token, (key, current)
                -> new Session(user, current.credentialFingerprint(),
                        current.createdAt(), now, now));
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    // Cierra todas las sesiones de un funcionario (cambio de clave, rol o estado).
    public void removeAllFor(int operatorId) {
        sessions.values().removeIf(session -> session.user().operatorId() == operatorId);
    }

    // Revoca la versión antigua sin cerrar accesos con una clave más reciente.
    public void removeSessionsWithCredential(int operatorId, String fingerprint) {
        Objects.requireNonNull(fingerprint, "La huella de la credencial es obligatoria.");
        sessions.values().removeIf(session -> session.user().operatorId() == operatorId
                && session.credentialFingerprint().equals(fingerprint));
    }

    // Revoca solo la versión reemplazada; una confirmación atrasada no cierra accesos nuevos.
    public void removeSupersededFor(AccountChange change) {
        OperatorAccount before = change.before();
        OperatorAccount after = change.after();
        if (before.equals(after)) return;
        String previousFingerprint = before.hasCredential()
                ? PasswordHasher.fingerprint(before.passwordHash()) : "";
        sessions.values().removeIf(session -> session.user().operatorId() == before.operatorId()
                && session.credentialFingerprint().equals(previousFingerprint)
                && session.user().role() == before.role()
                && session.user().username().equals(before.username())
                && session.user().name().equals(before.name()));
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> isExpired(session, now));
    }

    private static boolean isExpired(Session session, Instant now) {
        return !now.isBefore(session.lastUsedAt().plus(IDLE_TIMEOUT))
                || !now.isBefore(session.createdAt().plus(ABSOLUTE_TIMEOUT));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
