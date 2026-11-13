package cr.ac.una.repository;

import cr.ac.una.model.RecalledTicket;

// Define el rellamado del ticket ya asignado y devuelve la revisión guardada de su llamado.
public interface TicketRecallRepository {
    RecalledTicket recall(long assignmentId, long ticketId, int operatorId);
}
