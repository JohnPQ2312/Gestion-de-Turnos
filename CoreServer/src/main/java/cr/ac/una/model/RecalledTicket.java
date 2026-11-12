package cr.ac.una.model;

import java.time.LocalDateTime;
import java.util.Objects;

// Confirmación de un rellamado ya guardado, con su revisión persistente.
public record RecalledTicket(
        long assignmentId, long ticketId, int callCount, LocalDateTime lastCalledAt
) {
    public RecalledTicket {
        if (assignmentId <= 0 || ticketId <= 0 || callCount < 2) {
            throw new IllegalArgumentException("La confirmación del rellamado no es válida.");
        }
        Objects.requireNonNull(lastCalledAt, "La fecha del último llamado es obligatoria.");
    }
}
