package cr.ac.una.CoreServer;

import cr.ac.una.repository.InMemoryServiceRepository;
import cr.ac.una.repository.InMemoryTicketRepository;
import cr.ac.una.server.TurnRequestHandler;
import cr.ac.una.server.TurnServer;
import cr.ac.una.service.TicketService;
import java.io.IOException;

public class App {

    public static void main(String[] args) {
        InMemoryTicketRepository ticketRepository
                = new InMemoryTicketRepository();

        InMemoryServiceRepository serviceRepository
                = new InMemoryServiceRepository();

        TicketService ticketService
                = new TicketService(ticketRepository);

        TurnRequestHandler requestHandler
                = new TurnRequestHandler(
                        serviceRepository,
                        ticketService
                );

        TurnServer server = new TurnServer(5000, requestHandler);

        try {
            server.start();
        } catch (IOException exception) {
            System.err.println(
                    "No se pudo iniciar o mantener el servidor: "
                    + exception.getMessage()
            );
        }
    }
}
