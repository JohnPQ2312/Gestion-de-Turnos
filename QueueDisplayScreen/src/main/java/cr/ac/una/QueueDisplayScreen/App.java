package cr.ac.una.QueueDisplayScreen;

import cr.ac.una.QueueDisplayScreen.client.DisplayServerClient;
import cr.ac.una.QueueDisplayScreen.controller.DisplayScreenController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private DisplayScreenController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/QueueDisplayScreen/views/displayScreen.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();

        stage.setTitle("Pantalla de turnos");
        stage.setScene(new Scene(root, 900, 760));
        stage.setMinWidth(850);
        stage.setMinHeight(700);
        stage.show();

        DisplayServerClient client = new DisplayServerClient("127.0.0.1", 5000);
        controller.configure(client::getSnapshot);
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
