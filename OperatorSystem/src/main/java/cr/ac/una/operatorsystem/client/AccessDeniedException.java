package cr.ac.una.operatorsystem.client;

// Representa una respuesta DENIED de CoreServer con su motivo de acceso.
// Permite decidir si se informa la falta de permiso o se regresa al inicio de sesión.
public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reason;

    public AccessDeniedException(String reason, String message) {
        super(message);
        this.reason = reason == null ? "" : reason;
    }

    public String getReason() {
        return reason;
    }

    // La sesión ya no sirve: hay que volver a la pantalla de acceso.
    public boolean endsSession() {
        return "SESSION_EXPIRED".equals(reason)
                || "UNAUTHENTICATED".equals(reason)
                || "ACCOUNT_DISABLED".equals(reason);
    }
}
