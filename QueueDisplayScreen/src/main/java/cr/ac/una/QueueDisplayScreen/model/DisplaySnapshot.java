package cr.ac.una.QueueDisplayScreen.model;

import java.util.List;
import java.util.Objects;

// Agrupa las listas de espera y llamados recientes que se presentan en una misma actualización.
public record DisplaySnapshot(
        List<TicketRow> waitingTickets,
        List<TicketRow> recentCalls
        ) {

    public DisplaySnapshot {
        Objects.requireNonNull(
                waitingTickets,
                "La lista de turnos en espera es obligatoria."
        );

        Objects.requireNonNull(
                recentCalls,
                "La lista de llamados recientes es obligatoria."
        );

        waitingTickets = List.copyOf(waitingTickets);
        recentCalls = List.copyOf(recentCalls);
    }
}
