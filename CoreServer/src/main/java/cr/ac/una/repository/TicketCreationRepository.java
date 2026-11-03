package cr.ac.una.repository;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;

public interface TicketCreationRepository {

    Ticket createWithNextNumber(Service service, boolean priority);
}