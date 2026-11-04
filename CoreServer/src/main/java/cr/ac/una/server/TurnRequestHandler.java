package cr.ac.una.server;

import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.ActiveTicket;
import cr.ac.una.model.DisplaySnapshot;
import cr.ac.una.model.DisplayTicket;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.ServiceRepository;
import cr.ac.una.repository.ActiveTicketRepository;
import cr.ac.una.repository.DisplayRepository;
import cr.ac.una.service.TicketAttentionService;
import cr.ac.una.service.TicketLifecycleService;
import cr.ac.una.service.TicketService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TurnRequestHandler {

    private final ServiceRepository serviceRepository;
    private final TicketService ticketService;
    private final TicketAttentionService attentionService;
    private final ActiveTicketRepository activeTicketRepository;
    private final TicketLifecycleService lifecycleService;
    private final DisplayRepository displayRepository;

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository
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
        return "OK;ATTENTION_STARTED;" + assignmentId + ";" + ticketId;
    }

    private String finishAttention(String assignmentIdText, String ticketIdText) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        long ticketId = parseTicketId(ticketIdText);
        lifecycleService.finishAttention(assignmentId, ticketId);
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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
