package cr.ac.una.server;

import cr.ac.una.exception.AccessDeniedException;
import cr.ac.una.exception.DataAccessException;
import cr.ac.una.model.AuthenticatedUser;
import cr.ac.una.model.UserRole;
import cr.ac.una.service.AuthService;
import cr.ac.una.service.TicketRecallService;
import cr.ac.una.model.RecalledTicket;
import cr.ac.una.model.ActiveTicket;
import cr.ac.una.model.CalledTicket;
import cr.ac.una.model.ServiceWindow;
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
import cr.ac.una.service.WindowAvailabilityService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Interpreta una línea del protocolo, comprueba el acceso y delega la operación al servicio.
// Publica avisos a las pantallas después de las modificaciones confirmadas.
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
    // Límite de la espera en la captura V3. Los llamados y en atención se envían completos.
    private static final int PUBLIC_WAITING_LIMIT = 30;

    private final DisplayRepository displayRepository;
    private final DisplayEventHub displayEvents;
    private final ReportProtocolHandler reports;
    private final AuthService authService;
    private final WindowAvailabilityService windowAvailabilityService;
    private final TicketRecallService recallService;
    private final PeopleAdministrationHandler peopleAdministration;
    private final AssignmentConfigurationHandler assignmentConfiguration;
    private final CatalogAdministrationHandler catalogAdministration;

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository,
            DisplayEventHub displayEvents,
            ReportService reportService,
            AuthService authService,
            WindowAvailabilityService windowAvailabilityService,
            TicketRecallService recallService
    ) {
        this(serviceRepository, ticketService, attentionService, activeTicketRepository, lifecycleService, displayRepository, displayEvents, reportService, authService, windowAvailabilityService, recallService, null);
    }

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository,
            DisplayEventHub displayEvents,
            ReportService reportService,
            AuthService authService,
            WindowAvailabilityService windowAvailabilityService,
            TicketRecallService recallService,
            PeopleAdministrationHandler peopleAdministration
    ) {
        this(serviceRepository, ticketService, attentionService, activeTicketRepository, lifecycleService,
                displayRepository, displayEvents, reportService, authService, windowAvailabilityService,
                recallService, peopleAdministration, null);
    }

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository,
            DisplayEventHub displayEvents,
            ReportService reportService,
            AuthService authService,
            WindowAvailabilityService windowAvailabilityService,
            TicketRecallService recallService,
            PeopleAdministrationHandler peopleAdministration,
            AssignmentConfigurationHandler assignmentConfiguration
    ) {
        this(serviceRepository, ticketService, attentionService, activeTicketRepository, lifecycleService,
                displayRepository, displayEvents, reportService, authService, windowAvailabilityService,
                recallService, peopleAdministration, assignmentConfiguration, null);
    }

    public TurnRequestHandler(
            ServiceRepository serviceRepository,
            TicketService ticketService,
            TicketAttentionService attentionService,
            ActiveTicketRepository activeTicketRepository,
            TicketLifecycleService lifecycleService,
            DisplayRepository displayRepository,
            DisplayEventHub displayEvents,
            ReportService reportService,
            AuthService authService,
            WindowAvailabilityService windowAvailabilityService,
            TicketRecallService recallService,
            PeopleAdministrationHandler peopleAdministration,
            AssignmentConfigurationHandler assignmentConfiguration,
            CatalogAdministrationHandler catalogAdministration
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
        this.reports = new ReportProtocolHandler(Objects.requireNonNull(
                reportService, "El servicio de reportes es obligatorio."
        ));
        this.authService = Objects.requireNonNull(
                authService, "El servicio de acceso es obligatorio."
        );
        this.windowAvailabilityService = Objects.requireNonNull(
                windowAvailabilityService, "El servicio de disponibilidad es obligatorio."
        );
        this.recallService = Objects.requireNonNull(recallService, "El servicio de rellamado es obligatorio.");
        this.peopleAdministration = peopleAdministration;
        this.assignmentConfiguration = assignmentConfiguration;
        this.catalogAdministration = catalogAdministration;
    }

    // Acepta comandos públicos, LOGIN y solicitudes AUTH con token.
    // Convierte los rechazos de acceso en DENIED y los errores de operación en ERROR.
    public String handle(String request) {
        try {
            if (request == null || request.isBlank()) {
                throw new IllegalArgumentException("La solicitud está vacía.");
            }

            if (request.startsWith("LOGIN;")) {
                return login(request.split(";", -1));
            }

            if (request.startsWith("AUTH;")) {
                String[] envelope = request.split(";", 3);
                if (envelope.length < 3 || envelope[2].isBlank()) {
                    throw new IllegalArgumentException("La solicitud autenticada no es válida.");
                }

                if (envelope[2].startsWith("ADMIN_ROUTING_")) {
                    var actor = authService.administrationActor(envelope[1]);
                    if (assignmentConfiguration == null) throw new IllegalStateException("La configuración de atención no está disponible.");
                    return assignmentConfiguration.handle(envelope[2], actor);
                }
                if (envelope[2].startsWith("ADMIN_SERVICE_") || envelope[2].startsWith("ADMIN_WINDOW_")) {
                    var actor = authService.administrationActor(envelope[1]);
                    if (catalogAdministration == null) throw new IllegalStateException("La administración de servicios y ventanillas no está configurada.");
                    String response = catalogAdministration.handle(envelope[2], actor);
                    // Las pantallas muestran el nombre del servicio: se refrescan tras confirmar un cambio.
                    if (response.startsWith("OK;ADMIN_SERVICE_UPDATED;")) displayEvents.publishChange();
                    return response;
                }
                if (envelope[2].startsWith("ADMIN_PEOPLE_")) {
                    var actor = authService.administrationActor(envelope[1]);
                    if (peopleAdministration == null) throw new IllegalStateException("La administración de personas no está configurada.");
                    return peopleAdministration.handle(envelope[2], actor);
                }
                AuthenticatedUser user = authService.authenticate(envelope[1]);

                if ("LOGOUT".equals(envelope[2])) {
                    authService.logout(envelope[1]);
                    return "OK;LOGOUT";
                }

                return dispatch(envelope[2], user);
            }

            return dispatch(request, null);

        } catch (AccessDeniedException exception) {
            return "DENIED;" + exception.getReason().name() + ";" + encode(exception.getMessage());

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

    // user es null cuando la solicitud no trae sesión. Los comandos públicos
    // lo ignoran; los protegidos lo exigen y comprueban sus permisos.
    private String dispatch(String request, AuthenticatedUser user) {
        if (request.startsWith("ADMIN_PEOPLE_") || request.startsWith("ADMIN_ROUTING_")
                || request.startsWith("ADMIN_SERVICE_") || request.startsWith("ADMIN_WINDOW_")) authService.requireRole(user, UserRole.ADMIN);
        if ("LIST_SERVICES".equals(request)) {
            return listServices();
        }

        if ("GET_DISPLAY_SNAPSHOT".equals(request)) {
            return getDisplaySnapshot(false);
        }
        if ("GET_DISPLAY_SNAPSHOT_V2".equals(request)) {
            return getDisplaySnapshot(true);
        }
        if ("GET_DISPLAY_SNAPSHOT_V3".equals(request)) {
            return getActiveDisplaySnapshot();
        }

        if ("LIST_ASSIGNMENTS".equals(request)) {
            return listAssignments(authService.requireUser(user));
        }

        String[] parts = request.split(";", -1);

        if (parts.length == 3 && "GENERATE_TICKET".equals(parts[0])) {
            return generateTicket(parts);
        }

        if (parts.length == 2 && "CALL_NEXT".equals(parts[0])) {
            authService.requireOwnAssignment(user, parseAssignmentId(parts[1]));
            return callNext(parts[1], false);
        }

        // V2: confirmación completa con el identificador y el estado del turno, para continuar la
        // atención sin consultar GET_ACTIVE_TICKET. CALL_NEXT conserva su formato de seis campos.
        if (parts.length == 2 && "CALL_NEXT_V2".equals(parts[0])) {
            authService.requireOwnAssignment(user, parseAssignmentId(parts[1]));
            return callNext(parts[1], true);
        }

        if (parts.length == 2 && "GET_ACTIVE_TICKET".equals(parts[0])) {
            authService.requireOwnAssignment(user, parseAssignmentId(parts[1]));
            return getActiveTicket(parts[1]);
        }

        if (parts.length == 3 && "START_ATTENTION".equals(parts[0])) {
            authService.requireOwnAssignment(user, parseAssignmentId(parts[1]));
            return startAttention(parts[1], parts[2]);
        }

        if (parts.length == 3 && "FINISH_ATTENTION".equals(parts[0])) {
            authService.requireOwnAssignment(user, parseAssignmentId(parts[1]));
            return finishAttention(parts[1], parts[2]);
        }

        if (parts.length == 3 && "RECALL_TICKET".equals(parts[0])) {
            long assignmentId = parseAssignmentId(parts[1]);
            authService.requireOwnAssignment(user, assignmentId);
            RecalledTicket recalled = recallService.recall(
                    assignmentId, parseTicketId(parts[2]), user.operatorId());
            // El repositorio ya confirmó su transacción antes de publicar.
            displayEvents.publishChange();
            return "OK;RECALLED;" + recalled.assignmentId() + ";" + recalled.ticketId()
                    + ";" + recalled.callCount() + ";" + encode(recalled.lastCalledAt().toString());
        }

        // Estado operativo de la ventanilla de una asignación propia.
        // Las tres respuestas: OK;WINDOW_STATE;ventanilla;estado.
        if (parts.length == 2 && "GET_WINDOW_STATE".equals(parts[0])) {
            long assignmentId = parseAssignmentId(parts[1]);
            authService.requireOwnAssignment(user, assignmentId);
            return windowState(windowAvailabilityService.currentState(assignmentId));
        }

        if (parts.length == 2 && "PAUSE_WINDOW".equals(parts[0])) {
            long assignmentId = parseAssignmentId(parts[1]);
            authService.requireOwnAssignment(user, assignmentId);
            return windowState(windowAvailabilityService.pause(assignmentId));
        }

        if (parts.length == 2 && "RESUME_WINDOW".equals(parts[0])) {
            long assignmentId = parseAssignmentId(parts[1]);
            authService.requireOwnAssignment(user, assignmentId);
            return windowState(windowAvailabilityService.resume(assignmentId));
        }

        // Reportes de la sección 13: COMANDO;AAAA-MM-DD;AAAA-MM-DD. Solo ADMIN.
        // Solo leen datos y no publican avisos a las pantallas.
        if (parts.length == 3 && REPORT_COMMANDS.contains(parts[0])) {
            authService.requireRole(user, UserRole.ADMIN);
            return reports.handle(parts[0], parts[1], parts[2]);
        }

        throw new IllegalArgumentException(
                "El comando o su formato no es válido."
        );
    }

    // OK;LOGIN;token;id funcionario;nombre;usuario;rol
    private String login(String[] parts) {
        if (parts.length != 3) {
            throw new IllegalArgumentException("La solicitud de inicio de sesión no es válida.");
        }

        AuthService.LoginResult result = authService.login(
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8),
                URLDecoder.decode(parts[2], StandardCharsets.UTF_8)
        );
        AuthenticatedUser user = result.user();

        return "OK;LOGIN;" + result.token()
                + ";" + user.operatorId()
                + ";" + encode(user.name())
                + ";" + encode(user.username())
                + ";" + user.role().name();
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

    // Solo las asignaciones del funcionario autenticado: la consulta ya se limita a su identidad y el
    // filtro posterior queda como defensa adicional.
    private String listAssignments(AuthenticatedUser user) {
        List<OperatorWindowAssignment> assignments = attentionService.listActiveAssignments(user.operatorId())
                .stream()
                .filter(assignment -> assignment.getOperatorId() == user.operatorId())
                .toList();
        StringBuilder response = new StringBuilder("OK;ASSIGNMENTS;");
        response.append(assignments.size());

        for (OperatorWindowAssignment assignment : assignments) {
            response.append(";").append(assignment.getAssignmentId())
                    .append(";").append(assignment.getOperatorId())
                    .append(";").append(assignment.getWindowId());
        }

        return response.toString();
    }

    // Valida la selección y devuelve los datos del turno ya registrado por el servidor.
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

    // V1: OK;CALLED;código;servicio;funcionario;ventanilla (herramientas de concurrencia).
    // V2: OK;CALLED_V2;turno;código;servicio;asignación;funcionario;ventanilla;LLAMADO, los mismos
    // campos que ACTIVE_TICKET. Sin turno elegible ambas responden OK;NO_TICKET.
    private String callNext(String assignmentIdText, boolean complete) {
        long assignmentId = parseAssignmentId(assignmentIdText);
        Optional<CalledTicket> next = attentionService.callNext(assignmentId);

        if (next.isEmpty()) {
            return "OK;NO_TICKET";
        }

        Ticket ticket = next.get().ticket();
        displayEvents.publishChange();
        if (complete) {
            return "OK;CALLED_V2;"
                    + next.get().ticketId() + ";"
                    + encode(ticket.getVisualCode()) + ";"
                    + encode(ticket.getService().getName()) + ";"
                    + ticket.getAssignmentId() + ";"
                    + ticket.getOperatorId() + ";"
                    + ticket.getWindowId() + ";"
                    + ticket.getState().name();
        }
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

    // Lee espera y llamados recientes con sus límites de filas y los serializa (V1 o V2).
    private String getDisplaySnapshot(boolean withCallHistory) {
        return DisplaySnapshotProtocol.legacy(displayRepository.findSnapshot(50, 5), withCallHistory);
    }

    // V3: todos los llamados/en atención y como máximo PUBLIC_WAITING_LIMIT turnos en espera.
    private String getActiveDisplaySnapshot() {
        return DisplaySnapshotProtocol.active(displayRepository.findActiveSnapshot(PUBLIC_WAITING_LIMIT));
    }

    private String windowState(ServiceWindow window) {
        return "OK;WINDOW_STATE;" + window.getWindowId() + ";" + window.getState().name();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
