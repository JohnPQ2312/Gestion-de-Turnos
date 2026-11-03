package cr.ac.una.repository;

import cr.ac.una.model.Ticket;
import java.util.List;

public interface TicketRepository
        extends TicketCreationRepository, TicketCallingRepository {

    List<Ticket> findRegularTickets();

    List<Ticket> findPriorityTickets();
}
