package cr.ac.una.operatorsystem;

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
                App.class.getResource("/cr/ac/una/operatorsystem/views/operatorScreen.fxml")
        );
        Parent root = loader.load();
        stage.setTitle("Atención de turnos");
        stage.setScene(new Scene(root, 500, 300));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
