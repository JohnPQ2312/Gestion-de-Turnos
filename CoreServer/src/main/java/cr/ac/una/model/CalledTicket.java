package cr.ac.una.model;

import java.util.Objects;

// Resultado de un llamado confirmado: el identificador persistido del turno y sus datos.
// Permite responder al operador con todo lo necesario para continuar la atención sin otra consulta.
public record CalledTicket(long ticketId, Ticket ticket) {

    public CalledTicket {
        if (ticketId <= 0) {
            throw new IllegalArgumentException("El identificador del turno llamado debe ser positivo.");
        }
        Objects.requireNonNull(ticket, "El turno llamado es obligatorio.");
        if (ticket.getState() != TicketState.LLAMADO) {
            throw new IllegalArgumentException("El turno devuelto por el llamado debe estar LLAMADO.");
        }
    }
}
