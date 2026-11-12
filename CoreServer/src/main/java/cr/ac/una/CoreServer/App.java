package cr.ac.una.CoreServer;

import cr.ac.una.config.DatabaseConnectionFactory;
import cr.ac.una.repository.JdbcAccountRepository;
import cr.ac.una.repository.JdbcActiveTicketRepository;
import cr.ac.una.repository.JdbcDisplayRepository;
import cr.ac.una.repository.JdbcOperatorRepository;
import cr.ac.una.repository.JdbcOperatorWindowAssignmentRepository;
import cr.ac.una.repository.JdbcReportRepository;
import cr.ac.una.repository.JdbcServiceRepository;
import cr.ac.una.repository.JdbcServiceWindowRepository;
import cr.ac.una.repository.JdbcTicketCallingRepository;
import cr.ac.una.repository.JdbcTicketCreationRepository;
import cr.ac.una.repository.JdbcTicketLifecycleRepository;
import cr.ac.una.repository.JdbcWindowAvailabilityRepository;
import cr.ac.una.repository.ServiceRepository;
import cr.ac.una.repository.TicketCreationRepository;
import cr.ac.una.server.DisplayEventHub;
import cr.ac.una.server.TurnRequestHandler;
import cr.ac.una.server.TurnServer;
import cr.ac.una.security.PasswordHasher;
import cr.ac.una.security.SessionRegistry;
import cr.ac.una.service.AuthService;
import cr.ac.una.service.ReportService;
import cr.ac.una.service.TicketAttentionService;
import cr.ac.una.service.TicketLifecycleService;
import cr.ac.una.service.TicketService;
import cr.ac.una.service.TicketRecallService;
import cr.ac.una.repository.JdbcTicketRecallRepository;
import cr.ac.una.service.WindowAvailabilityService;
import java.io.IOException;
import java.time.Clock;
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

            DisplayEventHub displayEvents = new DisplayEventHub();

            TurnRequestHandler requestHandler = new TurnRequestHandler(
                    serviceRepository, ticketService, attentionService,
                    new JdbcActiveTicketRepository(connectionFactory), lifecycleService,
                    new JdbcDisplayRepository(connectionFactory), displayEvents,
                    new ReportService(new JdbcReportRepository(connectionFactory)),
                    new AuthService(
                            new JdbcAccountRepository(connectionFactory),
                            new PasswordHasher(),
                            new SessionRegistry(Clock.systemUTC()),
                            new JdbcOperatorWindowAssignmentRepository(connectionFactory)
                    ),
                    new WindowAvailabilityService(
                            new JdbcWindowAvailabilityRepository(connectionFactory)
                    ),
                    new TicketRecallService(new JdbcTicketRecallRepository(connectionFactory))
            );

            TurnServer server = new TurnServer(5000, requestHandler, displayEvents);
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
