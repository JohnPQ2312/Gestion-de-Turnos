package cr.ac.una.repository;

public interface TicketLifecycleRepository {

    void startAttention(long assignmentId, long ticketId);

    void finishAttention(long assignmentId, long ticketId);
}
