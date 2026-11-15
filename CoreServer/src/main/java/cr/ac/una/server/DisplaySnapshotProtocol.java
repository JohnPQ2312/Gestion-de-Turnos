package cr.ac.una.server;

import cr.ac.una.model.ActiveDisplaySnapshot;
import cr.ac.una.model.DisplaySnapshot;
import cr.ac.una.model.DisplayTicket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Serializa las capturas públicas ya leídas. No consulta ni limita: TurnRequestHandler pide al repositorio
// findSnapshot(50, 5) para V1/V2 y findActiveSnapshot(30) para V3, y entrega aquí el resultado.
final class DisplaySnapshotProtocol {

    private DisplaySnapshotProtocol() {
    }

    // V1: OK;DISPLAY_SNAPSHOT;<espera>;<recientes> y siete campos por turno.
    // V2: OK;DISPLAY_SNAPSHOT_V2;... con nueve campos por turno (último llamado y contador).
    static String legacy(DisplaySnapshot snapshot, boolean withCallHistory) {
        StringBuilder response = new StringBuilder(withCallHistory ? "OK;DISPLAY_SNAPSHOT_V2;" : "OK;DISPLAY_SNAPSHOT;");
        response.append(snapshot.waitingTickets().size())
                .append(";").append(snapshot.recentCalls().size());

        for (DisplayTicket ticket : snapshot.waitingTickets()) {
            appendDisplayTicket(response, ticket, withCallHistory);
        }

        for (DisplayTicket ticket : snapshot.recentCalls()) {
            appendDisplayTicket(response, ticket, withCallHistory);
        }

        return response.toString();
    }

    // V3: solo turnos activos. Encabezado OK;DISPLAY_SNAPSHOT_V3;<llamados>;<espera enviada>;
    // <total en espera>; después todos los llamados/en atención (por último llamado) y luego los
    // próximos en espera, nueve campos por ticket como en V2. Los llamados no tienen límite ni
    // consumen el de la espera.
    static String active(ActiveDisplaySnapshot snapshot) {
        StringBuilder response = new StringBuilder("OK;DISPLAY_SNAPSHOT_V3;");
        response.append(snapshot.calledTickets().size())
                .append(";").append(snapshot.waitingTickets().size())
                .append(";").append(snapshot.waitingTotal());

        for (DisplayTicket ticket : snapshot.calledTickets()) {
            appendDisplayTicket(response, ticket, true);
        }

        for (DisplayTicket ticket : snapshot.waitingTickets()) {
            appendDisplayTicket(response, ticket, true);
        }

        return response.toString();
    }

    private static void appendDisplayTicket(StringBuilder response, DisplayTicket ticket, boolean withCallHistory) {
        response.append(";").append(ticket.ticketId())
                .append(";").append(encode(ticket.visualCode()))
                .append(";").append(encode(ticket.serviceName()))
                .append(";").append(ticket.priority())
                .append(";").append(ticket.state().name())
                .append(";").append(ticket.windowId() == null ? "" : ticket.windowId())
                .append(";").append(ticket.calledAt() == null
                ? "" : encode(ticket.calledAt().toString()));
        if (withCallHistory) {
            response.append(";").append(ticket.lastCalledAt() == null
                    ? "" : encode(ticket.lastCalledAt().toString()))
                    .append(";").append(ticket.callCount());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
