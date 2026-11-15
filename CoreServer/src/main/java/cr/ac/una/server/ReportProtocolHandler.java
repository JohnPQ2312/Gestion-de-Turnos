package cr.ac.una.server;

import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.DurationReport;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.model.TicketDuration;
import cr.ac.una.service.ReportService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

// Interpreta el período de las cinco consultas, las pide a ReportService y serializa la respuesta.
// No autoriza: TurnRequestHandler exige ADMIN antes de llamar. Tampoco captura errores; los rechazos y fallos
// de base de datos llegan al manejador, que los convierte en ERROR.
final class ReportProtocolHandler {

    private final ReportService reportService;

    ReportProtocolHandler(ReportService reportService) {
        this.reportService = Objects.requireNonNull(
                reportService, "El servicio de reportes es obligatorio."
        );
    }

    // COMANDO;AAAA-MM-DD;AAAA-MM-DD ya separado. El período se valida antes de consultar.
    String handle(String command, String fromText, String toText) {
        return handleReport(command, parsePeriod(fromText, toText));
    }

    private ReportPeriod parsePeriod(String fromText, String toText) {
        return new ReportPeriod(parseDate(fromText, "inicial"), parseDate(toText, "final"));
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "La fecha " + label + " debe tener el formato AAAA-MM-DD."
            );
        }
    }

    private String handleReport(String command, ReportPeriod period) {
        return switch (command) {
            case "REPORT_ATTENDED" ->
                attendedReport(period);
            case "REPORT_BY_OPERATOR" ->
                operatorReport(period);
            case "REPORT_BY_SERVICE" ->
                serviceReport(period);
            case "REPORT_WAIT_TIMES" ->
                durationReport("REPORT_WAIT_TIMES", reportService.waitTimes(period));
            case "REPORT_ATTENTION_TIMES" ->
                durationReport("REPORT_ATTENTION_TIMES", reportService.attentionTimes(period));
            default ->
                throw new IllegalArgumentException("El reporte solicitado no existe.");
        };
    }

    // OK;REPORT_ATTENDED;n; y por turno: código;servicio;prioridad;ventanilla;
    // id funcionario;funcionario;generación;inicio;fin
    private String attendedReport(ReportPeriod period) {
        List<AttendedTicket> tickets = reportService.attendedTickets(period);
        StringBuilder response = new StringBuilder("OK;REPORT_ATTENDED;");
        response.append(tickets.size());

        for (AttendedTicket ticket : tickets) {
            response.append(";").append(encode(ticket.visualCode()))
                    .append(";").append(encode(ticket.serviceName()))
                    .append(";").append(ticket.priority())
                    .append(";").append(ticket.windowId())
                    .append(";").append(ticket.operatorId())
                    .append(";").append(encode(ticket.operatorName()))
                    .append(";").append(encode(ticket.createdAt().toString()))
                    .append(";").append(encode(ticket.startedAt().toString()))
                    .append(";").append(encode(ticket.endedAt().toString()));
        }

        return response.toString();
    }

    // OK;REPORT_BY_OPERATOR;n; y por funcionario: id;nombre;finalizados
    private String operatorReport(ReportPeriod period) {
        List<OperatorAttentionCount> counts = reportService.attentionByOperator(period);
        StringBuilder response = new StringBuilder("OK;REPORT_BY_OPERATOR;");
        response.append(counts.size());

        for (OperatorAttentionCount count : counts) {
            response.append(";").append(count.operatorId())
                    .append(";").append(encode(count.operatorName()))
                    .append(";").append(count.attendedTickets());
        }

        return response.toString();
    }

    // OK;REPORT_BY_SERVICE;n; y por servicio: código;nombre;emitidos
    private String serviceReport(ReportPeriod period) {
        List<ServiceTicketCount> counts = reportService.ticketsByService(period);
        StringBuilder response = new StringBuilder("OK;REPORT_BY_SERVICE;");
        response.append(counts.size());

        for (ServiceTicketCount count : counts) {
            response.append(";").append(encode(count.serviceCode()))
                    .append(";").append(encode(count.serviceName()))
                    .append(";").append(count.issuedTickets());
        }

        return response.toString();
    }

    // OK;<COMANDO>;n;promedio;mínimo;máximo; y por turno:
    // código;servicio;desde;hasta;segundos
    private String durationReport(String command, DurationReport report) {
        StringBuilder response = new StringBuilder("OK;").append(command).append(";");
        response.append(report.tickets().size())
                .append(";").append(report.averageSeconds())
                .append(";").append(report.minimumSeconds())
                .append(";").append(report.maximumSeconds());

        for (TicketDuration ticket : report.tickets()) {
            response.append(";").append(encode(ticket.visualCode()))
                    .append(";").append(encode(ticket.serviceName()))
                    .append(";").append(encode(ticket.startedAt().toString()))
                    .append(";").append(encode(ticket.endedAt().toString()))
                    .append(";").append(ticket.seconds());
        }

        return response.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
