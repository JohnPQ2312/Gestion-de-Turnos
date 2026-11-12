package cr.ac.una.operatorsystem;

import cr.ac.una.operatorsystem.client.OperatorServerClient;
import cr.ac.una.operatorsystem.client.ReportServerClient;
import cr.ac.una.operatorsystem.controller.OperatorController;
import cr.ac.una.operatorsystem.controller.ReportsController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class App extends Application {

    private OperatorController controller;
    private Stage reportsStage;
    private ReportsController reportsController;

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

        ReportServerClient reportClient = new ReportServerClient("127.0.0.1", 5000);
        controller.configureReports(() -> openReports(stage, reportClient));
    }

    /**
     * Abre la vista de consultas en una ventana aparte, o la trae al frente
     * si ya estaba abierta. La pantalla de atención no se modifica.
     */
    private void openReports(Stage owner, ReportServerClient reportClient) {
        if (reportsStage != null) {
            reportsStage.show();
            reportsStage.toFront();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/cr/ac/una/operatorsystem/views/reports.fxml")
            );
            Parent root = loader.load();
            reportsController = loader.getController();
            reportsController.configure(reportClient::fetch);

            reportsStage = new Stage();
            reportsStage.initOwner(owner);
            reportsStage.setTitle("Consultas");
            reportsStage.setScene(new Scene(root, 1100, 700));
            reportsStage.setMinWidth(860);
            reportsStage.setMinHeight(560);
            reportsStage.show();

        } catch (IOException exception) {
            System.err.println("No se pudo abrir la vista de consultas: " + exception);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir la vista de consultas.");
            alert.initOwner(owner);
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
        if (reportsController != null) {
            reportsController.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
