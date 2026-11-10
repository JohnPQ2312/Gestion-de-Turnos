package cr.ac.una.operatorsystem;

import cr.ac.una.operatorsystem.client.OperatorServerClient;
import cr.ac.una.operatorsystem.controller.OperatorController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private OperatorController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/operatorsystem/views/operatorScreen.fxml")
        );
        Parent root = loader.load();
        controller = loader.getController();

        stage.setTitle("Atención de turnos");
        stage.setScene(new Scene(root, 1180, 780));
        stage.setMinWidth(900);
        stage.setMinHeight(680);
        stage.show();

        OperatorServerClient client = new OperatorServerClient("127.0.0.1", 5000);
        controller.configure(
                client::listAssignments, client::callNext, client::getActiveTicket,
                client::startAttention, client::finishAttention
        );
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
