package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import cr.ac.una.operatorsystem.client.AssignmentRequests;
import cr.ac.una.operatorsystem.model.AssignmentConfiguration;
import cr.ac.una.operatorsystem.model.AssignmentConfiguration.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public class AssignmentsController {
    @FXML private BorderPane content;
    @FXML private ComboBox<String> ownerType;
    @FXML private ComboBox<Owner> ownerCombo;
    @FXML private TableView<Permission> servicesTable;
    @FXML private Button enableButton;
    @FXML private Button removeButton;
    @FXML private ComboBox<OperatorOption> operatorCombo;
    @FXML private ComboBox<WindowOption> windowCombo;
    @FXML private Button openButton;
    @FXML private Button closeButton;
    @FXML private TableView<AssignmentEntry> assignmentsTable;
    @FXML private Label selectedAssignmentLabel;
    @FXML private Label noticeLabel;
    @FXML private Button reloadButton;

    public record Owner(int id, String label) { @Override public String toString() { return label; } }
    public record Permission(String code, String name, boolean active, boolean enabled) { }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private AssignmentConfiguration data;
    private AssignmentRequests requests;
    private Consumer<String> sessionEnded;
    private Task<?> currentTask;
    private boolean busy, closed, refreshRequired;

    @FXML private void initialize() {
        ownerType.getItems().setAll("Funcionario", "Ventanilla");
        ownerType.getSelectionModel().selectFirst();
        ownerType.valueProperty().addListener((o, old, value) -> showOwners());
        ownerCombo.valueProperty().addListener((o, old, value) -> showServices());
        servicesTable.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> updateControls());
        operatorCombo.valueProperty().addListener((o, old, value) -> updateControls());
        windowCombo.valueProperty().addListener((o, old, value) -> updateControls());
        assignmentsTable.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> updateControls());
        column(servicesTable, "Código", Permission::code, 110);
        column(servicesTable, "Servicio", Permission::name, 320);
        column(servicesTable, "Estado del servicio", p -> p.active() ? "Activo" : "Inactivo", 160);
        column(servicesTable, "Habilitación", p -> p.enabled() ? "Habilitado" : "Sin habilitar", 160);
        column(assignmentsTable, "Asignación", p -> "#" + p.id(), 110);
        column(assignmentsTable, "Funcionario", this::operatorName, 220);
        column(assignmentsTable, "Ventanilla", p -> "" + p.windowId(), 110);
        column(assignmentsTable, "Estado", p -> p.endedAt() == null ? "Abierta" : "Cerrada", 110);
        column(assignmentsTable, "Inicio", p -> DATE.format(p.startedAt()), 190);
        column(assignmentsTable, "Fin", p -> p.endedAt() == null ? "—" : DATE.format(p.endedAt()), 190);
        content.setDisable(true);
        updateControls();
    }
    public void configure(AssignmentRequests requests, Consumer<String> sessionEnded) {
        this.requests = Objects.requireNonNull(requests); this.sessionEnded = Objects.requireNonNull(sessionEnded); reload();
    }
    private static <T> void column(TableView<T> table, String title, Function<T, String> text, int width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(v -> new ReadOnlyStringWrapper(text.apply(v.getValue()))); c.setPrefWidth(width); table.getColumns().add(c);
    }
    private String operatorName(AssignmentEntry entry) {
        return data == null ? "" + entry.operatorId() : data.operators().stream().filter(o -> o.id() == entry.operatorId())
                .map(o -> o.id() + " - " + o.name()).findFirst().orElse("" + entry.operatorId());
    }
    @FXML private void reload() { load("Configuración actualizada. Seleccione una fila para modificarla."); }
    private void load(String message) {
        execute(requests::snapshot, snapshot -> {
            int previousOwner = ownerCombo.getValue() == null ? 0 : ownerCombo.getValue().id();
            Integer previousOperator = operatorCombo.getValue() == null ? null : operatorCombo.getValue().id();
            Integer previousWindow = windowCombo.getValue() == null ? null : windowCombo.getValue().id();
            data = snapshot; refreshRequired = false; content.setDisable(false);
            showOwners(); ownerCombo.getItems().stream().filter(o -> o.id() == previousOwner).findFirst().ifPresent(ownerCombo::setValue);
            operatorCombo.getItems().setAll(snapshot.operators()); windowCombo.getItems().setAll(snapshot.windows());
            snapshot.operators().stream().filter(o -> Objects.equals(o.id(), previousOperator)).findFirst().ifPresent(operatorCombo::setValue);
            snapshot.windows().stream().filter(w -> Objects.equals(w.id(), previousWindow)).findFirst().ifPresent(windowCombo::setValue);
            assignmentsTable.getItems().setAll(snapshot.assignments()); updateControls(); noticeLabel.setText(message);
        }, false);
    }
    private void showOwners() {
        if (data == null) return;
        ownerCombo.getItems().clear();
        if ("Funcionario".equals(ownerType.getValue())) {
            for (var o : data.operators()) ownerCombo.getItems().add(new Owner(o.id(), o.toString()));
        } else {
            for (var w : data.windows()) ownerCombo.getItems().add(new Owner(w.id(), w.toString()));
        }
        showServices();
    }
    private void showServices() {
        servicesTable.getItems().clear(); Owner owner = ownerCombo.getValue();
        if (data != null && owner != null) {
            var links = "Funcionario".equals(ownerType.getValue()) ? data.operatorServices() : data.windowServices();
            for (var service : data.services()) {
                boolean enabled = links.stream().anyMatch(l -> l.ownerId() == owner.id() && l.serviceCode().equals(service.code()));
                servicesTable.getItems().add(new Permission(service.code(), service.name(), service.active(), enabled));
            }
        }
        updateControls();
    }
    private void updateControls() {
        Permission selected = servicesTable.getSelectionModel().getSelectedItem();
        enableButton.setDisable(busy || closed || selected == null || selected.enabled() || !selected.active());
        removeButton.setDisable(busy || closed || selected == null || !selected.enabled());
        var operator = operatorCombo.getValue(); var window = windowCombo.getValue();
        openButton.setDisable(busy || closed || operator == null || window == null || !operator.active()
                || !operator.hasCredential() || !"ACTIVA".equals(window.state()) || !java.util.Set.of("ADMIN", "OPERADOR").contains(operator.role()));
        AssignmentEntry assignment = assignmentsTable.getSelectionModel().getSelectedItem();
        closeButton.setDisable(busy || closed || assignment == null || assignment.endedAt() != null);
        selectedAssignmentLabel.setText(assignment == null ? "Seleccione una asignación para consultar su estado o cerrarla."
                : "Asignación #" + assignment.id() + " · " + operatorName(assignment) + " · Ventanilla " + assignment.windowId()
                + (assignment.endedAt() == null ? " · Abierta" : " · Cerrada"));
    }
    @FXML private void enableService() { changeService(true); }
    @FXML private void removeService() { changeService(false); }
    private void changeService(boolean enabled) {
        if (busy || closed || ownerCombo.getValue() == null || servicesTable.getSelectionModel().getSelectedItem() == null) return;
        Owner owner = ownerCombo.getValue(); Permission permission = servicesTable.getSelectionModel().getSelectedItem();
        boolean operator = "Funcionario".equals(ownerType.getValue());
        if (!confirm((enabled ? "Habilitar " : "Retirar ") + permission.name() + " para " + owner.label() + ".")) return;
        execute(() -> {
            if (operator) requests.operatorService(owner.id(), permission.code(), enabled);
            else requests.windowService(owner.id(), permission.code(), enabled);
            return true;
        }, result -> load("Habilitación guardada. Los próximos llamados comprobarán la configuración actual."), true);
    }
    @FXML private void openAssignment() {
        if (busy || closed || operatorCombo.getValue() == null || windowCombo.getValue() == null) return;
        var operator = operatorCombo.getValue(); var window = windowCombo.getValue();
        if (!confirm("Abrir una asignación de " + operator.name() + " en la ventanilla " + window.id() + ".")) return;
        execute(() -> requests.open(operator.id(), window.id()), id -> load("Asignación #" + id
                + " abierta. El funcionario puede usar Actualizar asignaciones en su pantalla de atención."), true);
    }
    @FXML private void closeAssignment() {
        AssignmentEntry row = assignmentsTable.getSelectionModel().getSelectedItem();
        if (busy || closed || row == null || row.endedAt() != null) return;
        if (!confirm("Cerrar la asignación #" + row.id() + " de " + operatorName(row)
                + " en la ventanilla " + row.windowId() + ". Se conservarán las atenciones y el historial.")) return;
        execute(() -> { requests.close(row.id()); return row.id(); }, id -> load("Asignación #" + id
                + " cerrada. El funcionario puede actualizar su lista de asignaciones."), true);
    }
    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(noticeLabel.getScene().getWindow()); alert.setTitle("Configuración de atención"); alert.setHeaderText("Confirmar cambio");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private <T> void execute(Callable<T> action, Consumer<T> success, boolean write) {
        if (busy || closed || requests == null) return;
        busy = true; content.setDisable(true); reloadButton.setDisable(true); updateControls();
        noticeLabel.setText(write ? "Guardando…" : "Consultando…");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        currentTask = task;
        task.setOnSucceeded(event -> { if (!closed) { release(); success.accept(task.getValue()); } });
        task.setOnFailed(event -> {
            if (closed) return;
            release(); Throwable error = task.getException();
            if (error instanceof AccessDeniedException denied) {
                noticeLabel.setText(denied.getMessage());
                if (denied.endsSession() || "FORBIDDEN".equals(denied.getReason())) sessionEnded.accept(denied.getMessage());
            } else if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
                noticeLabel.setText(error.getMessage());
            } else {
                if (write) { refreshRequired = true; content.setDisable(true); }
                noticeLabel.setText(write ? "No se recibió una confirmación válida. El cambio pudo guardarse: actualice y compruebe la habilitación o el historial antes de repetir. No se reenvió la operación."
                        : "No se pudo actualizar la configuración. Los datos visibles pueden estar desactualizados; compruebe CoreServer y vuelva a consultar.");
            }
        });
        Thread.ofVirtual().name("operatorsystem-assignments").start(task);
    }
    private void release() { busy = false; currentTask = null; content.setDisable(refreshRequired); reloadButton.setDisable(false); updateControls(); }
    public void close() { closed = true; if (currentTask != null) currentTask.cancel(true); }
}
