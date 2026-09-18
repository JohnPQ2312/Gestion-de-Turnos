package cr.ac.una.costumerturn;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import cr.ac.una.Controller.TicketsGeneratorController;
import cr.ac.una.Controller.DisplayScreenController;
import cr.ac.una.repository.InMemoryTicketRepository;
import cr.ac.una.service.TicketService;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        InMemoryTicketRepository ticketRepository
                = new InMemoryTicketRepository();

        TicketService ticketService
                = new TicketService(ticketRepository);

        FXMLLoader generatorLoader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/Views/ticketsGenerator.fxml")
        );

        Parent generatorRoot = generatorLoader.load();

        TicketsGeneratorController generatorController
                = generatorLoader.getController();

        generatorController.setTicketService(ticketService);

        FXMLLoader displayLoader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/Views/displayScreen.fxml")
        );

        Parent displayRoot = displayLoader.load();

        DisplayScreenController displayController
                = displayLoader.getController();

        displayController.setTicketRepository(ticketRepository);

        generatorController.setOnTicketGenerated(
                () -> displayController.refreshTickets()
        );

        scene = new Scene(generatorRoot, 640, 480);
        stage.setTitle("Generador de turnos");
        stage.setScene(scene);

        Stage displayStage = new Stage();
        displayStage.initOwner(stage);
        displayStage.setTitle("Pantalla de turnos");
        displayStage.setScene(new Scene(displayRoot, 700, 420));

        stage.show();
        displayStage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}
