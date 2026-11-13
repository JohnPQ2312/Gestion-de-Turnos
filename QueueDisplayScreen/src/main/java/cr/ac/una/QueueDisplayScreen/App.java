package cr.ac.una.QueueDisplayScreen;

import cr.ac.una.QueueDisplayScreen.network.NetworkSettings;
import cr.ac.una.QueueDisplayScreen.client.DisplayServerClient;
import cr.ac.una.QueueDisplayScreen.controller.DisplayScreenController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

// Carga la pantalla pública y conecta las consultas y suscripciones con CoreServer.
// Al cerrar la aplicación se detiene el reloj y el trabajo de actualización del controlador.
public class App extends Application {

    private DisplayScreenController controller;

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
                App.class.getResource("/cr/ac/una/QueueDisplayScreen/Views/displayScreen.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();

        stage.setTitle("Pantalla de turnos");
        stage.setScene(new Scene(root, 1180, 860));
        stage.setMinWidth(900);
        stage.setMinHeight(740);
        stage.show();

        DisplayServerClient client = new DisplayServerClient(network.host(), network.port());
        controller.configure(client::getSnapshot, client::subscribe);
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
