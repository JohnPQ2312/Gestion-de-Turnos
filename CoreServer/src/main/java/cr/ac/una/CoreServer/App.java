package cr.ac.una.CoreServer;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.repository.JdbcActiveTicketRepository;
import cr.ac.una.repository.JdbcDisplayRepository;
import cr.ac.una.repository.JdbcOperatorRepository;
import cr.ac.una.repository.JdbcOperatorWindowAssignmentRepository;
import cr.ac.una.repository.JdbcServiceRepository;
import cr.ac.una.repository.JdbcServiceWindowRepository;
import cr.ac.una.repository.JdbcTicketCallingRepository;
import cr.ac.una.repository.JdbcTicketCreationRepository;
import cr.ac.una.repository.JdbcTicketLifecycleRepository;
import cr.ac.una.repository.ServiceRepository;
import cr.ac.una.repository.TicketCreationRepository;
import cr.ac.una.server.TurnRequestHandler;
import cr.ac.una.server.TurnServer;
import cr.ac.una.service.TicketAttentionService;
import cr.ac.una.service.TicketLifecycleService;
import cr.ac.una.service.TicketService;
import java.io.IOException;
import java.sql.SQLException;

public class App {

    public static void main(String[] args) {
        try {
            DatabaseConnectionFactory connectionFactory
                    = new DatabaseConnectionFactory();

            ServiceRepository serviceRepository
                    = new JdbcServiceRepository(connectionFactory);

            TicketCreationRepository ticketRepository
                    = new JdbcTicketCreationRepository(connectionFactory);

            TicketService ticketService = new TicketService(ticketRepository);

            TicketAttentionService attentionService = new TicketAttentionService(
                    new JdbcTicketCallingRepository(connectionFactory),
                    new JdbcOperatorRepository(connectionFactory),
                    new JdbcServiceWindowRepository(connectionFactory),
                    new JdbcOperatorWindowAssignmentRepository(connectionFactory)
            );

            TicketLifecycleService lifecycleService = new TicketLifecycleService(
                    new JdbcTicketLifecycleRepository(connectionFactory)
            );

            TurnRequestHandler requestHandler = new TurnRequestHandler(
                    serviceRepository, ticketService, attentionService,
                    new JdbcActiveTicketRepository(connectionFactory), lifecycleService,
                    new JdbcDisplayRepository(connectionFactory)
            );

            TurnServer server = new TurnServer(5000, requestHandler);
            server.start();

        } catch (SQLException exception) {
            System.err.println(
                    "No se pudo cargar la configuración de MySQL: "
                    + exception.getMessage()
            );

        } catch (IOException exception) {
            System.err.println(
                    "No se pudo iniciar o mantener el servidor: "
                    + exception.getMessage()
            );
        }
    }
}
