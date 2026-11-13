package cr.ac.una.costumerturn;

import cr.ac.una.costumerturn.network.NetworkSettings;
import cr.ac.una.costumerturn.client.TurnServerClient;
import cr.ac.una.costumerturn.controller.TicketsGeneratorController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Carga el generador y conecta sus solicitudes con CoreServer.
// El controlador recibe funciones de red y la aplicación cancela su trabajo al cerrar.
public class App extends Application {

    private TicketsGeneratorController controller;

    @Override
    public void start(Stage stage) throws IOException {
        NetworkSettings network;
        try { network=NetworkSettings.load(); }
        catch (IOException error) {
            javafx.scene.control.Alert alert=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "No se pudo cargar la configuración de red: " + error.getMessage());
            alert.initOwner(stage); alert.setHeaderText("Configuración de red"); alert.showAndWait();
            javafx.application.Platform.exit(); return;
        }
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/costumerturn/views/ticketsGenerator.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();

        stage.setTitle("Generador de turnos");
        stage.setScene(new Scene(root, 1024, 720));
        stage.setMinWidth(840);
        stage.setMinHeight(660);
        stage.show();

        TurnServerClient client = new TurnServerClient(network.host(), network.port());
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
