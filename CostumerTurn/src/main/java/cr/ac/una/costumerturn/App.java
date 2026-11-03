package cr.ac.una.costumerturn;

import cr.ac.una.costumerturn.client.TurnServerClient;
import cr.ac.una.costumerturn.controller.TicketsGeneratorController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private TicketsGeneratorController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/costumerturn/views/ticketsGenerator.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();

        stage.setTitle("Generador de turnos");
        stage.setScene(new Scene(root, 640, 480));
        stage.show();

        TurnServerClient client = new TurnServerClient("127.0.0.1", 5000);
        controller.configure(client::listServices, client::generateTicket);
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
