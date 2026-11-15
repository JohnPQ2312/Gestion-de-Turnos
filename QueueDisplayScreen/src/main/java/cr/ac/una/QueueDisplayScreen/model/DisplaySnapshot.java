package cr.ac.una.QueueDisplayScreen.model;

import java.util.List;
import java.util.Objects;

// Captura pública de turnos activos que se presenta en una misma actualización.
// calledTickets contiene todos los LLAMADO y EN_ATENCION ordenados por último llamado (más
// reciente primero); waitingTickets, los próximos EN_ESPERA en el orden de atención. Solo la
// espera puede llegar recortada: waitingTotal indica cuántos turnos esperan en total.
public record DisplaySnapshot(
        List<TicketRow> calledTickets,
        List<TicketRow> waitingTickets,
        int waitingTotal
        ) {

    public DisplaySnapshot {
        Objects.requireNonNull(calledTickets, "La lista de turnos llamados es obligatoria.");
        Objects.requireNonNull(waitingTickets, "La lista de turnos en espera es obligatoria.");

        calledTickets = List.copyOf(calledTickets);
        waitingTickets = List.copyOf(waitingTickets);

        for (TicketRow ticket : calledTickets) {
            if (!"LLAMADO".equals(ticket.state()) && !"EN_ATENCION".equals(ticket.state())) {
                throw new IllegalArgumentException(
                        "La lista de llamados solo admite turnos llamados o en atención."
                );
            }
        }
        for (TicketRow ticket : waitingTickets) {
            if (!"EN_ESPERA".equals(ticket.state())) {
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

    public boolean waitingTruncated() {
        return waitingTotal > waitingTickets.size();
    }
}
