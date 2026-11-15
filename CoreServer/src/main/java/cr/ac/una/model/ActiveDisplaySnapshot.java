package cr.ac.una.model;

import java.util.List;
import java.util.Objects;

// Captura pública de turnos activos: todos los llamados y en atención por un lado, y los próximos
// en espera por otro. Solo la espera puede estar limitada; waitingTotal indica cuántos existen.
// Los turnos finalizados no forman parte de esta captura.
public record ActiveDisplaySnapshot(
        List<DisplayTicket> calledTickets,
        List<DisplayTicket> waitingTickets,
        int waitingTotal
        ) {

    public ActiveDisplaySnapshot {
        Objects.requireNonNull(calledTickets, "La lista de turnos llamados es obligatoria.");
        Objects.requireNonNull(waitingTickets, "La lista de turnos en espera es obligatoria.");

        calledTickets = List.copyOf(calledTickets);
        waitingTickets = List.copyOf(waitingTickets);

        for (DisplayTicket ticket : calledTickets) {
            if (ticket.state() != TicketState.LLAMADO && ticket.state() != TicketState.EN_ATENCION) {
                throw new IllegalArgumentException(
                        "La lista de llamados solo admite turnos llamados o en atención."
                );
            }
        }
        for (DisplayTicket ticket : waitingTickets) {
            if (ticket.state() != TicketState.EN_ESPERA) {
                throw new IllegalArgumentException(
                        "La lista de espera solo admite turnos en espera."
                );
            }
        }
        if (waitingTotal < waitingTickets.size()) {
            throw new IllegalArgumentException(
                    "El total en espera no puede ser menor que la lista recibida."
            );
        }
    }
}
