package cr.ac.una.repository;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import java.util.List;

public interface TicketRepository {

    // Crea y guarda un turno con el siguiente consecutivo de su servicio, de forma atómica.
    Ticket createWithNextNumber(Service service, boolean priority);

    List<Ticket> findRegularTickets();

    List<Ticket> findPriorityTickets();
}
