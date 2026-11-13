package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import cr.ac.una.operatorsystem.model.UserSession;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

// Envía las credenciales a CoreServer en un hilo virtual y entrega la sesión recibida a App.
// El servidor verifica la contraseña y decide si concede el acceso.
public class LoginController {

    @FunctionalInterface
    public interface LoginRequest {

        UserSession login(String username, String password) throws IOException;
    }

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label errorLabel;
    @FXML
    private Label noticeLabel;

    private LoginRequest loginRequest;
    private Consumer<UserSession> onLoggedIn;
    private Task<UserSession> currentTask;
    private boolean busy;
    private boolean closed;

    @FXML
    private void initialize() {
        hide(errorLabel);
        hide(noticeLabel);
        updateControls();
    }

    public void configure(LoginRequest loginRequest, Consumer<UserSession> onLoggedIn) {
        this.loginRequest = Objects.requireNonNull(loginRequest, "El inicio de sesión es obligatorio.");
        this.onLoggedIn = Objects.requireNonNull(onLoggedIn, "La continuación es obligatoria.");
        updateControls();
        Platform.runLater(usernameField::requestFocus);
    }

    // Mensaje informativo, por ejemplo al volver por sesión vencida.
    public void showNotice(String message) {
        if (message != null && !message.isBlank()) {
            noticeLabel.setText(message);
            show(noticeLabel);
        }
    }

    @FXML
    private void login() {
        if (busy || closed || loginRequest == null) {
            return;
        }

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Ingrese el usuario y la contraseña.");
            return;
        }

        hide(errorLabel);
        hide(noticeLabel);

        Task<UserSession> task = new Task<>() {
            @Override
            protected UserSession call() throws IOException {
                return loginRequest.login(username, password);
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            passwordField.clear();
            setBusy(false);
            onLoggedIn.accept(task.getValue());
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            Throwable error = task.getException();
            passwordField.clear();
            setBusy(false);

            if (error instanceof AccessDeniedException || error instanceof IllegalArgumentException) {
                showError(error.getMessage());
            } else {
                System.err.println("No se pudo iniciar sesión. Detalle: " + error);
                showError("No se pudo conectar con CoreServer. Compruebe que esté en ejecución "
                        + "y vuelva a intentarlo.");
            }
            passwordField.requestFocus();
        });

        currentTask = task;
        setBusy(true);
        Thread.ofVirtual().name("operatorsystem-login").start(task);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        show(errorLabel);
    }

    private static void show(Label label) {
        label.setVisible(true);
        label.setManaged(true);
    }

    private static void hide(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    private void updateControls() {
        boolean blocked = closed || busy;
        usernameField.setDisable(blocked);
        passwordField.setDisable(blocked);
        loginButton.setDisable(blocked || loginRequest == null);
        loginButton.setText(busy ? "Ingresando..." : "Iniciar sesión");
    }

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        passwordField.clear();
        updateControls();
    }
}
