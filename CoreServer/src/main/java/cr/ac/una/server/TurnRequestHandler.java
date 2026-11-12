package cr.ac.una.server;

import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.ActiveTicket;
import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.DurationReport;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.model.TicketDuration;
import cr.ac.una.model.DisplaySnapshot;
import cr.ac.una.model.DisplayTicket;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.ServiceRepository;
import cr.ac.una.repository.ActiveTicketRepository;
import cr.ac.una.repository.DisplayRepository;
import cr.ac.una.service.ReportService;
import cr.ac.una.service.TicketAttentionService;
import cr.ac.una.service.TicketLifecycleService;
import cr.ac.una.service.TicketService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TurnRequestHandler {

    private static final Set<String> REPORT_COMMANDS = Set.of(
            "REPORT_ATTENDED", "REPORT_BY_OPERATOR", "REPORT_BY_SERVICE",
            "REPORT_WAIT_TIMES", "REPORT_ATTENTION_TIMES"
    );

    private final ServiceRepository serviceRepository;
    private final TicketService ticketService;
    private final TicketAttentionService attentionService;
    private final ActiveTicketRepository activeTicketRepository;
    private final TicketLifecycleService lifecycleService;
    private final DisplayRepository displayRepository;
    private final DisplayEventHub displayEvents;
    private final ReportService reportService;

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository,
            DisplayEventHub displayEvents,
            ReportService reportService
    ) {
        this.serviceRepository = Objects.requireNonNull(
                serviceRepository, "El repositorio de servicios es obligatorio."
        );
        this.ticketService = Objects.requireNonNull(
                ticketService, "El servicio de turnos es obligatorio."
        );
        this.attentionService = Objects.requireNonNull(
                attentionService, "El servicio de atención es obligatorio."
        );
        this.activeTicketRepository = Objects.requireNonNull(
                activeTicketRepository, "El repositorio de turnos activos es obligatorio."
        );
        this.lifecycleService = Objects.requireNonNull(
                lifecycleService, "El servicio del ciclo de atención es obligatorio."
        );
        this.displayRepository = Objects.requireNonNull(
                displayRepository, "El repositorio de la pantalla pública es obligatorio."
        );
        this.displayEvents = Objects.requireNonNull(
                displayEvents, "El centro de avisos de la pantalla es obligatorio."
        );
        this.reportService = Objects.requireNonNull(
                reportService, "El servicio de reportes es obligatorio."
        );
    }

    public String handle(String request) {
        try {
            if (request == null || request.isBlank()) {
                throw new IllegalArgumentException("La solicitud está vacía.");
            }

            if ("LIST_SERVICES".equals(request)) {
                return listServices();
            }

            if ("LIST_ASSIGNMENTS".equals(request)) {
                return listAssignments();
            }

            if ("GET_DISPLAY_SNAPSHOT".equals(request)) {
                return getDisplaySnapshot();
            }

            String[] parts = request.split(";", -1);

            if (parts.length == 3 && "GENERATE_TICKET".equals(parts[0])) {
                return generateTicket(parts);
            }

            if (parts.length == 2 && "CALL_NEXT".equals(parts[0])) {
                return callNext(parts[1]);
            }

            if (parts.length == 2 && "GET_ACTIVE_TICKET".equals(parts[0])) {
                return getActiveTicket(parts[1]);
            }

            if (parts.length == 3 && "START_ATTENTION".equals(parts[0])) {
                return startAttention(parts[1], parts[2]);
            }

            if (parts.length == 3 && "FINISH_ATTENTION".equals(parts[0])) {
                return finishAttention(parts[1], parts[2]);
            }

            // Reportes de la sección 13: COMANDO;AAAA-MM-DD;AAAA-MM-DD.
            // Solo leen datos y no publican avisos a las pantallas.
            // Pendiente: restringirlos al perfil administrador cuando exista
            // la autenticación.
            if (parts.length == 3 && REPORT_COMMANDS.contains(parts[0])) {
                return handleReport(parts[0], parsePeriod(parts[1], parts[2]));
            }

            throw new IllegalArgumentException(
                    "El comando o su formato no es válido."
            );

        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "ERROR;" + encode(exception.getMessage());

        } catch (DataAccessException exception) {
            System.err.println(exception.getMessage());
            exception.printStackTrace(System.err);

            return "ERROR;" + encode(
                    "No se pudo completar la operación con la base de datos."
            );
        }
    }

    private String listServices() {
        List<Service> services = serviceRepository.findAll();
        StringBuilder response = new StringBuilder("OK;SERVICES;");
        response.append(services.size());

        for (Service service : services) {
            response.append(";")
                    .append(encode(service.getCode()))
                    .append(";")
                    .append(encode(service.getName()));
        }

        return response.toString();
    }

    private String listAssignments() {
        List<OperatorWindowAssignment> assignments
                = attentionService.listActiveAssignments();
        StringBuilder response = new StringBuilder("OK;ASSIGNMENTS;");
        response.append(assignments.size());

        for (OperatorWindowAssignment assignment : assignments) {
            response.append(";").append(assignment.getAssignmentId())
                    .append(";").append(assignment.getOperatorId())
                    .append(";").append(assignment.getWindowId());
        }

        return response.toString();
    }

    private String generateTicket(String[] parts) {
        String serviceCode = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);

        if (!"true".equals(parts[2]) && !"false".equals(parts[2])) {
            throw new IllegalArgumentException("La prioridad debe ser true o false.");
        }

        boolean priority = Boolean.parseBoolean(parts[2]);
        Service service = serviceRepository.findByCode(serviceCode)
                .orElseThrow(() -> new IllegalArgumentException(
                "El servicio solicitado no existe."
        ));

        Ticket ticket = ticketService.generateTicket(service, priority);
        displayEvents.publishChange();

        return "OK;TICKET;"
                + encode(ticket.getVisualCode()) + ";"
                + encode(ticket.getService().getName()) + ";"
                + ticket.isPriority();
    }

    private String callNext(String assignmentIdText) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        Optional<Ticket> next = attentionService.callNext(assignmentId);

        if (next.isEmpty()) {
            return "OK;NO_TICKET";
        }

        Ticket ticket = next.get();
        displayEvents.publishChange();
        return "OK;CALLED;"
                + encode(ticket.getVisualCode()) + ";"
                + encode(ticket.getService().getName()) + ";"
                + ticket.getOperatorId() + ";"
                + ticket.getWindowId();
    }

    private String getActiveTicket(String assignmentIdText) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        Optional<ActiveTicket> active = activeTicketRepository.findByAssignmentId(assignmentId);

        if (active.isEmpty()) {
            return "OK;NO_ACTIVE_TICKET";
        }

        ActiveTicket ticket = active.get();
        return "OK;ACTIVE_TICKET;"
                + ticket.ticketId() + ";"
                + encode(ticket.visualCode()) + ";"
                + encode(ticket.serviceName()) + ";"
                + ticket.assignmentId() + ";"
                + ticket.operatorId() + ";"
                + ticket.windowId() + ";"
                + ticket.state().name();
    }

    private long parseAssignmentId(String assignmentIdText) {
        return parsePositiveId(assignmentIdText, "El identificador de la asignación");
    }

    private long parseTicketId(String ticketIdText) {
        return parsePositiveId(ticketIdText, "El identificador del turno");
    }

    private long parsePositiveId(String value, String label) {
        long identifier;
        try {
            identifier = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    label + " debe ser un número entero."
            );
        }

        if (identifier <= 0) {
            throw new IllegalArgumentException(
                    label + " debe ser positivo."
            );
        }
        return identifier;
    }

    private String startAttention(String assignmentIdText, String ticketIdText) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        long ticketId = parseTicketId(ticketIdText);
        lifecycleService.startAttention(assignmentId, ticketId);
        displayEvents.publishChange();
        return "OK;ATTENTION_STARTED;" + assignmentId + ";" + ticketId;
    }

    private String finishAttention(String assignmentIdText, String ticketIdText) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        long ticketId = parseTicketId(ticketIdText);
        lifecycleService.finishAttention(assignmentId, ticketId);
        displayEvents.publishChange();
        return "OK;ATTENTION_FINISHED;" + assignmentId + ";" + ticketId;
    }

    private String getDisplaySnapshot() {
        DisplaySnapshot snapshot = displayRepository.findSnapshot(50, 5);
        StringBuilder response = new StringBuilder("OK;DISPLAY_SNAPSHOT;");
        response.append(snapshot.waitingTickets().size())
                .append(";").append(snapshot.recentCalls().size());

        for (DisplayTicket ticket : snapshot.waitingTickets()) {
            appendDisplayTicket(response, ticket);
        }

        for (DisplayTicket ticket : snapshot.recentCalls()) {
            appendDisplayTicket(response, ticket);
        }

        return response.toString();
    }

    private void appendDisplayTicket(StringBuilder response, DisplayTicket ticket) {
        response.append(";").append(ticket.ticketId())
                .append(";").append(encode(ticket.visualCode()))
                .append(";").append(encode(ticket.serviceName()))
                .append(";").append(ticket.priority())
                .append(";").append(ticket.state().name())
                .append(";").append(ticket.windowId() == null ? "" : ticket.windowId())
                .append(";").append(ticket.calledAt() == null
                ? "" : encode(ticket.calledAt().toString()));
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
