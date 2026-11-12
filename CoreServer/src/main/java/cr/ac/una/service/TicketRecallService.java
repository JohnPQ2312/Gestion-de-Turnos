package cr.ac.una.service;

import cr.ac.una.model.RecalledTicket;
import cr.ac.una.repository.TicketRecallRepository;
import java.util.Objects;

public class TicketRecallService {
    private final TicketRecallRepository repository;

    public TicketRecallService(TicketRecallRepository repository) {
        this.repository = Objects.requireNonNull(repository, "El repositorio de rellamados es obligatorio.");
    }

    public RecalledTicket recall(long assignmentId, long ticketId, int operatorId) {
        if (assignmentId <= 0 || ticketId <= 0 || operatorId <= 0) {
            throw new IllegalArgumentException("Los identificadores del rellamado deben ser positivos.");
        }
        return repository.recall(assignmentId, ticketId, operatorId);
    }
}
