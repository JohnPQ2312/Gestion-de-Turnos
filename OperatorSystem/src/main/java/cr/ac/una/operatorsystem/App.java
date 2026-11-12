package cr.ac.una.operatorsystem;

import cr.ac.una.operatorsystem.network.NetworkSettings;
import cr.ac.una.operatorsystem.client.AuthServerClient;
import cr.ac.una.operatorsystem.client.PeopleServerClient;
import cr.ac.una.operatorsystem.client.AssignmentServerClient;
import cr.ac.una.operatorsystem.controller.AssignmentsController;
import cr.ac.una.operatorsystem.controller.PeopleController;
import cr.ac.una.operatorsystem.client.OperatorServerClient;
import cr.ac.una.operatorsystem.client.ReportServerClient;
import cr.ac.una.operatorsystem.controller.LoginController;
import cr.ac.una.operatorsystem.controller.OperatorController;
import cr.ac.una.operatorsystem.controller.ReportsController;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Flujo de OperatorSystem: pantalla de acceso, atención con la sesión
 * iniciada y regreso al acceso al cerrar sesión o cuando el servidor la da
 * por terminada. La sesión solo vive en memoria.
 */
public class App extends Application {

    private String host;
    private int port;
    // Plazo del aviso de cierre al salir de la aplicación.
    private static final int EXIT_LOGOUT_TIMEOUT_MS = 2000;

    private AuthServerClient authClient;

    private Stage stage;
    private UserSession session;
    private LoginController loginController;
    private OperatorController controller;
    private Stage assignmentsStage;
    private AssignmentsController assignmentsController;
    private Stage peopleStage;
    private PeopleController peopleController;
    private Stage reportsStage;
    private ReportsController reportsController;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        NetworkSettings network;
        try { network=NetworkSettings.load(); }
        catch (IOException error) {
            javafx.scene.control.Alert alert=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    "No se pudo cargar la configuración de red: " + error.getMessage());
            alert.initOwner(stage); alert.setHeaderText("Configuración de red"); alert.showAndWait();
            javafx.application.Platform.exit(); return;
        }

        host=network.host(); port=network.port();
        authClient=new AuthServerClient(host,port);
        stage.setTitle("Atención de turnos");
        stage.setMinWidth(900);
        stage.setMinHeight(680);
        showLogin(null);
        stage.show();
    }

    private void showLogin(String notice) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/cr/ac/una/operatorsystem/views/login.fxml")
            );
            Parent root = loader.load();
            loginController = loader.getController();
            loginController.configure(authClient::login, this::showOperator);
            loginController.showNotice(notice);
            setRoot(root);

        } catch (IOException exception) {
            showOpenError("No se pudo abrir la pantalla de acceso.", exception);
        }
    }

    private void showOperator(UserSession newSession) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/cr/ac/una/operatorsystem/views/operatorScreen.fxml")
            );
            Parent root = loader.load();
            OperatorController newController = loader.getController();

            closeLogin();
            session = newSession;
            controller = newController;
            setRoot(root);

            OperatorServerClient client = new OperatorServerClient(host, port, session);
            controller.configure(
                    client::listAssignments, client::callNext, client::getActiveTicket,
                    client::startAttention, client::finishAttention
            );
            controller.configureRecall(client::recallTicket);
            controller.configurePause(
                    client::getWindowState, client::pauseWindow, client::resumeWindow
            );
            controller.configureSession(
                    session.name() + " · " + session.username() + " · " + session.role(),
                    this::logout,
                    this::sessionEnded
            );

            if (session.isAdmin()) {
                ReportServerClient reportClient = new ReportServerClient(host, port, session);
                controller.configureReports(() -> openReports(reportClient));
                PeopleServerClient peopleClient = new PeopleServerClient(host, port, session);
                controller.configurePeople(() -> openPeople(peopleClient));
                AssignmentServerClient assignmentClient = new AssignmentServerClient(host, port, session);
                controller.configureAssignments(() -> openAssignments(assignmentClient));
            } else {
                controller.hideReports();
            }

        } catch (IOException exception) {
            showOpenError("No se pudo abrir la pantalla de atención.", exception);
        }
    }

    /** Cierre pedido por el usuario. */
    private void logout() {
        UserSession closing = session;
        closeSessionViews();
        showLogin("Sesión cerrada.");

        // Se avisa al servidor en segundo plano; si no responde, la sesión
        // vence sola por inactividad.
        if (closing != null) {
            Thread.ofVirtual().name("operatorsystem-logout").start(() -> {
                try {
                    authClient.logout(closing);
                } catch (IOException | RuntimeException exception) {
                    System.err.println("No se pudo avisar el cierre de sesión: " + exception.getMessage());
                }
            });
        }
    }

    /** El servidor rechazó la sesión: vencida, cerrada o cuenta inactiva. */
    private void sessionEnded(String message) {
        if (session == null) {
            return;
        }
        closeSessionViews();
        showLogin(message);
    }

    private void closeSessionViews() {
        session = null;
        if (controller != null) {
            controller.close();
            controller = null;
        }
        if (assignmentsController != null) {
            assignmentsController.close();
            assignmentsController = null;
        }
        if (assignmentsStage != null) {
            assignmentsStage.close();
            assignmentsStage = null;
        }
        if (peopleController != null) {
            peopleController.close();
            peopleController = null;
        }
        if (peopleStage != null) {
            peopleStage.close();
            peopleStage = null;
        }
        if (reportsController != null) {
            reportsController.close();
            reportsController = null;
        }
        if (reportsStage != null) {
            reportsStage.close();
            reportsStage = null;
        }
    }

    private void closeLogin() {
        if (loginController != null) {
            loginController.close();
            loginController = null;
        }
    }

    /**
     * Abre la vista de consultas en una ventana aparte, o la trae al frente
     * si ya estaba abierta. La pantalla de atención no se modifica.
     */
    private void openReports(ReportServerClient reportClient) {
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
            reportsController.configure(reportClient::fetch, this::sessionEnded);

            reportsStage = new Stage();
            reportsStage.initOwner(stage);
            reportsStage.setTitle("Consultas");
            reportsStage.setScene(new Scene(root, 1100, 700));
            reportsStage.setMinWidth(860);
            reportsStage.setMinHeight(560);
            reportsStage.show();

        } catch (IOException exception) {
            showOpenError("No se pudo abrir la vista de consultas.", exception);
        }
    }

    private void openPeople(PeopleServerClient client) {
        if (peopleStage != null) {
            peopleStage.show();
            peopleStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("/cr/ac/una/operatorsystem/views/people.fxml"));
            Parent root = loader.load();
            peopleController = loader.getController();
            peopleStage = new Stage();
            peopleStage.initOwner(stage);
            peopleStage.setTitle("Administración de personas");
            peopleStage.setScene(new Scene(root, 1180, 780));
            peopleStage.setMinWidth(980);
            peopleStage.setMinHeight(650);
            peopleStage.setOnHidden(event -> {
                if (peopleController != null) peopleController.close();
                peopleController = null;
                peopleStage = null;
            });
            peopleController.configure(client, session.operatorId(), this::sessionEnded);
            peopleStage.show();
        } catch (IOException exception) {
            showOpenError("No se pudo abrir la administración de personas.", exception);
        }
    }

    private void openAssignments(AssignmentServerClient client) {
        if (assignmentsStage != null) { assignmentsStage.show(); assignmentsStage.toFront(); return; }
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("/cr/ac/una/operatorsystem/views/assignments.fxml"));
            Parent root = loader.load();
            assignmentsController = loader.getController();
            assignmentsStage = new Stage();
            assignmentsStage.initOwner(stage);
            assignmentsStage.setTitle("Configuración de atención");
            assignmentsStage.setScene(new Scene(root, 1180, 780));
            assignmentsStage.setMinWidth(980);
            assignmentsStage.setMinHeight(650);
            assignmentsStage.setOnHidden(event -> {
                if (assignmentsController != null) assignmentsController.close();
                assignmentsController = null;
                assignmentsStage = null;
            });
            assignmentsController.configure(client, this::sessionEnded);
            assignmentsStage.show();
        } catch (IOException exception) {
            showOpenError("No se pudo abrir la configuración de atención.", exception);
        }
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            stage.setScene(new Scene(root, 1180, 780));
        } else {
            stage.getScene().setRoot(root);
        }
    }

    private void showOpenError(String message, IOException exception) {
        System.err.println(message + " " + exception);
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /**
     * Al salir, el aviso de cierre se envía en otro hilo y el hilo de JavaFX
     * no espera la red. Ese hilo no es daemon: el proceso lo espera como
     * máximo unos EXIT_LOGOUT_TIMEOUT_MS (conexión y lectura). Si el servidor
     * no responde a tiempo, la sesión no se da por revocada: vence en
     * CoreServer por inactividad (30 min) o al reiniciarlo.
     */
    @Override
    public void stop() {
        UserSession closing = session;
        closeSessionViews();
        closeLogin();

        if (closing != null) {
            Thread.ofPlatform().daemon(false).name("operatorsystem-logout-on-exit").start(() -> {
                try {
                    authClient.logout(closing, EXIT_LOGOUT_TIMEOUT_MS, EXIT_LOGOUT_TIMEOUT_MS);
                } catch (IOException | RuntimeException exception) {
                    System.err.println("No se confirmó el cierre de sesión al salir; "
                            + "vencerá en el servidor. Detalle: " + exception.getMessage());
                }
            });
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
