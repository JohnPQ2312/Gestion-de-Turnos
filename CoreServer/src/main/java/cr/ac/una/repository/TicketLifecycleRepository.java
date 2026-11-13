package cr.ac.una.repository;

// Define el inicio y la finalización de atención sobre un ticket de la asignación indicada.
public interface TicketLifecycleRepository {

    void startAttention(long assignmentId, long ticketId);

    void finishAttention(long assignmentId, long ticketId);
}
