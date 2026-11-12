package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import cr.ac.una.operatorsystem.client.PeopleRequests;
import cr.ac.una.operatorsystem.model.ManagedAppUser;
import cr.ac.una.operatorsystem.model.ManagedOperator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public class PeopleController {
    @FXML private BorderPane content;
    @FXML private TableView<ManagedOperator> operatorsTable;
    @FXML private TableView<ManagedAppUser> usersTable;
    @FXML private TextField operatorName;
    @FXML private TextField operatorUsername;
    @FXML private ComboBox<String> operatorRole;
    @FXML private CheckBox operatorActive;
    @FXML private PasswordField operatorPassword;
    @FXML private Label operatorSelection;
    @FXML private Label credentialLabel;
    @FXML private Button saveOperatorButton;
    @FXML private Button resetPasswordButton;
    @FXML private TextField userUsername;
    @FXML private CheckBox userActive;
    @FXML private Label userSelection;
    @FXML private Button saveUserButton;
    @FXML private Button reloadButton;
    @FXML private Label noticeLabel;

    private PeopleRequests requests;
    private Consumer<String> sessionEnded;
    private int selfId;
    private ManagedOperator selectedOperator;
    private ManagedAppUser selectedUser;
    private Task<?> currentTask;
    private boolean closed;
    private boolean busy;
    private boolean refreshRequired;

    private record Listings(List<ManagedOperator> operators, List<ManagedAppUser> users) { }

    @FXML
    private void initialize() {
        column(operatorsTable, "Id", r -> "" + r.id(), 60);
        column(operatorsTable, "Nombre", ManagedOperator::name, 220);
        column(operatorsTable, "Acceso", ManagedOperator::username, 150);
        column(operatorsTable, "Rol", ManagedOperator::role, 110);
        column(operatorsTable, "Estado", r -> r.active() ? "Activo" : "Inactivo", 100);
        column(usersTable, "Id", r -> "" + r.id(), 60);
        column(usersTable, "Usuario", ManagedAppUser::username, 260);
        column(usersTable, "Estado", r -> r.active() ? "Activo" : "Inactivo", 100);
        operatorRole.getItems().setAll("OPERADOR", "ADMIN");
        operatorsTable.getSelectionModel().selectedItemProperty().addListener((o, before, row) -> selectOperator(row));
        usersTable.getSelectionModel().selectedItemProperty().addListener((o, before, row) -> selectUser(row));
        selectOperator(null);
        selectUser(null);
        noticeLabel.setText("Pendiente de consulta.");
        content.setDisable(true);
    }

    public void configure(PeopleRequests requests, int selfId, Consumer<String> sessionEnded) {
        this.requests = Objects.requireNonNull(requests);
        this.selfId = selfId;
        this.sessionEnded = Objects.requireNonNull(sessionEnded);
        reload();
    }

    private static <T> void column(TableView<T> table, String title, Function<T, String> value, int width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
    }

    @FXML
    private void reload() {
        load("Datos actualizados. Seleccione una fila para editar o use Nuevo.");
    }

    private void load(String success) {
        execute(() -> new Listings(requests.operators(), requests.users()), data -> {
            refreshRequired = false;
            content.setDisable(false);
            operatorsTable.getItems().setAll(data.operators());
            usersTable.getItems().setAll(data.users());
            newOperator();
            newUser();
            noticeLabel.setText(success);
        }, false);
    }

    private void selectOperator(ManagedOperator row) {
        selectedOperator = row;
        operatorName.setText(row == null ? "" : row.name());
        operatorUsername.setText(row == null ? "" : row.username());
        operatorRole.setValue(row == null ? "OPERADOR" : row.role());
        operatorActive.setSelected(row == null || row.active());
        operatorActive.setDisable(row == null);
        operatorPassword.clear();
        operatorSelection.setText(row == null ? "Nuevo funcionario" : "Funcionario #" + row.id());
        credentialLabel.setText(row == null ? "La contraseña inicial es obligatoria (8 a 128 caracteres)."
                : (row.hasCredential() ? "Tiene credencial de acceso." : "Sin credencial: configure una contraseña para permitir el acceso.")
                + (row.hasOpenAssignment() ? " Tiene una asignación abierta; su rol y estado están protegidos." : ""));
        saveOperatorButton.setText(row == null ? "Crear funcionario" : "Guardar cambios");
        resetPasswordButton.setDisable(row == null);
    }

    private void selectUser(ManagedAppUser row) {
        selectedUser = row;
        userUsername.setText(row == null ? "" : row.username());
        userActive.setSelected(row == null || row.active());
        userActive.setDisable(row == null);
        userSelection.setText(row == null ? "Nuevo usuario" : "Usuario #" + row.id());
        saveUserButton.setText(row == null ? "Crear usuario" : "Guardar cambios");
    }

    @FXML private void newOperator() { operatorsTable.getSelectionModel().clearSelection(); selectOperator(null); }
    @FXML private void newUser() { usersTable.getSelectionModel().clearSelection(); selectUser(null); }

    @FXML
    private void saveOperator() {
        if (busy || closed) return;
        ManagedOperator row = selectedOperator;
        String name = operatorName.getText();
        String username = operatorUsername.getText();
        String role = operatorRole.getValue();
        boolean active = operatorActive.isSelected();
        String password = operatorPassword.getText();
        if (name.isBlank() || username.isBlank() || role == null || (row == null && password.isEmpty())) {
            noticeLabel.setText("Complete nombre, acceso, rol y, para un alta, contraseña inicial.");
            return;
        }
        if (row != null && !password.isEmpty()) {
            noticeLabel.setText("Para cambiar la contraseña use Restablecer contraseña; Guardar cambios solo modifica los datos.");
            return;
        }
        if (!confirm(row == null ? "Crear el funcionario " + username + "."
                : "Guardar los datos del funcionario #" + row.id() + ". Sus sesiones anteriores pueden cerrarse."
                + (row.id() == selfId ? " Está editando su propia cuenta y deberá iniciar sesión de nuevo." : ""))) return;
        operatorPassword.clear();
        execute(() -> {
            if (row == null) return requests.createOperator(name, username, role, password);
            requests.updateOperator(row.id(), name, username, role, active);
            return row.id();
        }, id -> {
            if (row != null && id == selfId) sessionEnded.accept("Su cuenta fue actualizada. Inicie sesión de nuevo.");
            else load("Funcionario #" + id + " guardado. Seleccione su fila para consultar los datos actuales.");
        }, true);
    }

    @FXML
    private void resetPassword() {
        if (busy || closed || selectedOperator == null) return;
        int id = selectedOperator.id();
        String password = operatorPassword.getText();
        if (password.length() < 8 || password.length() > 128) {
            noticeLabel.setText("Ingrese una contraseña nueva de 8 a 128 caracteres.");
            return;
        }
        if (!confirm("Restablecer la contraseña del funcionario #" + id + ". Sus sesiones con la contraseña anterior dejarán de servir."
                + (id == selfId ? " Deberá iniciar sesión de nuevo." : ""))) return;
        operatorPassword.clear();
        execute(() -> { requests.resetPassword(id, password); return id; }, result -> {
            if (id == selfId) sessionEnded.accept("Su contraseña cambió. Inicie sesión de nuevo.");
            else load("Contraseña del funcionario #" + id + " actualizada.");
        }, true);
    }

    @FXML
    private void saveUser() {
        if (busy || closed) return;
        ManagedAppUser row = selectedUser;
        String username = userUsername.getText();
        boolean active = userActive.isSelected();
        if (username.isBlank()) { noticeLabel.setText("Ingrese el nombre del usuario."); return; }
        if (!confirm(row == null ? "Crear el usuario " + username + "."
                : "Guardar los datos y el estado del usuario #" + row.id() + ".")) return;
        execute(() -> {
            if (row == null) return requests.createUser(username);
            requests.updateUser(row.id(), username, active);
            return row.id();
        }, id -> load("Usuario #" + id + " guardado. El historial se conserva."), true);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(noticeLabel.getScene().getWindow());
        alert.setTitle("Administración de personas");
        alert.setHeaderText("Confirmar cambio");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private <T> void execute(Callable<T> action, Consumer<T> success, boolean write) {
        if (busy || closed || requests == null) return;
        busy = true;
        content.setDisable(true);
        reloadButton.setDisable(true);
        noticeLabel.setText(write ? "Guardando…" : "Consultando…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        currentTask = task;
        task.setOnSucceeded(event -> {
            if (closed) return;
            release();
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            if (closed) return;
            release();
            Throwable error = task.getException();
            operatorPassword.clear();
            if (error instanceof AccessDeniedException denied) {
                noticeLabel.setText(denied.getMessage());
                if (denied.endsSession()) sessionEnded.accept(denied.getMessage());
            } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
                noticeLabel.setText(error.getMessage());
            } else {
                if (write) { refreshRequired = true; content.setDisable(true); }
                noticeLabel.setText(write
                        ? "No se recibió una confirmación válida. El cambio pudo guardarse: actualice los datos antes de repetir. Para contraseñas, compruebe el acceso. No se reenvió la operación."
                        : "No se pudieron actualizar los datos; las filas visibles pueden estar desactualizadas. Compruebe CoreServer y presione Actualizar.");
            }
        });
        Thread.ofVirtual().name("operatorsystem-people").start(task);
    }

    private void release() {
        busy = false;
        currentTask = null;
        content.setDisable(refreshRequired);
        reloadButton.setDisable(false);
    }

    public void close() {
        closed = true;
        if (currentTask != null) currentTask.cancel(true);
        operatorPassword.clear();
    }
}
