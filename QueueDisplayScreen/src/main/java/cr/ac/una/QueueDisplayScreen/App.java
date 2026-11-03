package cr.ac.una.QueueDisplayScreen;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                App.class.getResource("/cr/ac/una/QueueDisplayScreen/views/displayScreen.fxml")
        );
        Parent root = loader.load();
        stage.setTitle("Pantalla de turnos");
        stage.setScene(new Scene(root, 700, 420));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
