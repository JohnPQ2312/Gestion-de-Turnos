package cr.ac.una.repository;

import cr.ac.una.model.Ticket;
import java.util.List;

// Combina emisión y llamado para la implementación en memoria y permite consultar sus dos colas.
public interface TicketRepository
        extends TicketCreationRepository, TicketCallingRepository {

    List<Ticket> findRegularTickets();

    List<Ticket> findPriorityTickets();
}
