package cr.ac.una.service;

import cr.ac.una.repository.TicketLifecycleRepository;
import java.util.Objects;

public class TicketLifecycleService {

    private final TicketLifecycleRepository lifecycleRepository;

    public TicketLifecycleService(
            TicketLifecycleRepository lifecycleRepository
    ) {
        this.lifecycleRepository = Objects.requireNonNull(
                lifecycleRepository,
                "El repositorio del ciclo de atención es obligatorio."
        );
    }

    public void startAttention(long assignmentId, long ticketId) {
        validateIdentifiers(assignmentId, ticketId);
        lifecycleRepository.startAttention(assignmentId, ticketId);
    }

    public void finishAttention(long assignmentId, long ticketId) {
        validateIdentifiers(assignmentId, ticketId);
        lifecycleRepository.finishAttention(assignmentId, ticketId);
    }

    private void validateIdentifiers(long assignmentId, long ticketId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        if (ticketId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del turno debe ser positivo."
            );
        }
    }
}
