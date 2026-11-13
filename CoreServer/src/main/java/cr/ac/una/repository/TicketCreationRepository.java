package cr.ac.una.repository;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;

// Define la creación del turno junto con la obtención de su consecutivo por servicio.
public interface TicketCreationRepository {

    Ticket createWithNextNumber(Service service, boolean priority);
}