package cr.ac.una.repository;

import cr.ac.una.model.RecalledTicket;

public interface TicketRecallRepository {
    RecalledTicket recall(long assignmentId, long ticketId, int operatorId);
}
