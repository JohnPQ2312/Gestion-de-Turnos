package cr.ac.una.model;

import java.util.List;
import java.util.Objects;

// Agrupa espera y llamados recientes en listas que no cambian después de construir la captura.
public record DisplaySnapshot(
        List<DisplayTicket> waitingTickets,
        List<DisplayTicket> recentCalls
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