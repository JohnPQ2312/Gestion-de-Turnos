package cr.ac.una.server;

import cr.ac.una.model.Service;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.InMemoryServiceRepository;
import cr.ac.una.service.TicketService;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;

public class TurnRequestHandler {

    private final InMemoryServiceRepository serviceRepository;
    private final TicketService ticketService;

    public TurnRequestHandler(
            InMemoryServiceRepository serviceRepository,
            TicketService ticketService
    ) {
        this.serviceRepository = Objects.requireNonNull(
                serviceRepository,
                "El repositorio de servicios es obligatorio."
        );
        this.ticketService = Objects.requireNonNull(
                ticketService,
                "El servicio de turnos es obligatorio."
        );
    }

    public String handle(String request) {
        try {
            if (request == null || request.isBlank()) {
                throw new IllegalArgumentException(
                        "La solicitud está vacía."
                );
            }

            if ("LIST_SERVICES".equals(request)) {
                return listServices();
            }

            String[] parts = request.split(";", -1);

            if (parts.length != 3
                    || !"GENERATE_TICKET".equals(parts[0])) {
                throw new IllegalArgumentException(
                        "El comando o su formato no es válido."
                );
            }

            String serviceCode = URLDecoder.decode(
                    parts[1], StandardCharsets.UTF_8
            );

            if (!"true".equals(parts[2])
                    && !"false".equals(parts[2])) {
                throw new IllegalArgumentException(
                        "La prioridad debe ser true o false."
                );
            }

            boolean priority = Boolean.parseBoolean(parts[2]);

            Service service = serviceRepository
                    .findByCode(serviceCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                    "El servicio solicitado no existe."
            ));

            Ticket ticket = ticketService.generateTicket(
                    service, priority
            );

            return "OK;TICKET;"
                    + encode(ticket.getVisualCode()) + ";"
                    + encode(ticket.getService().getName()) + ";"
                    + ticket.isPriority();

        } catch (IllegalArgumentException exception) {
            return "ERROR;" + encode(exception.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(
                value, StandardCharsets.UTF_8
        );
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
}
