package cr.ac.una.model;

import java.util.List;
import java.util.Objects;

/** Resultado de las consultas 4 y 5: el detalle por turno y su resumen. */
public record DurationReport(
        List<TicketDuration> tickets,
        long averageSeconds,
        long minimumSeconds,
        long maximumSeconds
        ) {

    public DurationReport {
        tickets = List.copyOf(Objects.requireNonNull(
                tickets, "La lista de turnos es obligatoria."
        ));
    }

    /** Calcula el resumen; con una lista vacía, los tres valores son 0. */
    public static DurationReport of(List<TicketDuration> tickets) {
        if (tickets.isEmpty()) {
            return new DurationReport(tickets, 0, 0, 0);
        }

        long total = 0;
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;

        for (TicketDuration ticket : tickets) {
            total += ticket.seconds();
            minimum = Math.min(minimum, ticket.seconds());
            maximum = Math.max(maximum, ticket.seconds());
        }

        long average = Math.round((double) total / tickets.size());
        return new DurationReport(tickets, average, minimum, maximum);
    }
}
