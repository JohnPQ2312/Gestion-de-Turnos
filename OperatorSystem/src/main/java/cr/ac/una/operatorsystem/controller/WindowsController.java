package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.CatalogRequests;
import cr.ac.una.operatorsystem.model.ManagedWindow;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

// Vista de administración de ventanillas (bloque 2). Las habilitaciones y asignaciones siguen en Asignaciones;
// la pausa la maneja el operador desde Atención. Aquí se crean, se cambia la modalidad y se da de baja o se reactiva.
public class WindowsController {

    private record ModeOption(String value) {
        @Override public String toString() { return ManagedWindow.modeLabel(value) + ("MIXED".equals(value) ? " (regulares y preferenciales)" : ""); }
    }
    private static final ModeOption MIXED = new ModeOption("MIXED");
    private static final ModeOption PRIORITY_ONLY = new ModeOption("PRIORITY_ONLY");

    @FXML private BorderPane content;
    @FXML private TableView<ManagedWindow> windowsTable;
    @FXML private Label selectionLabel;
    @FXML private TextField numberField;
    @FXML private ComboBox<ModeOption> modeCombo;
    @FXML private Label detailLabel;
    @FXML private Button saveButton;
    @FXML private Button stateButton;
    @FXML private Button reloadButton;
    @FXML private Label noticeLabel;

    private CatalogRequests requests;
    private AdminTaskRunner runner;
    private ManagedWindow selected;

    @FXML
    private void initialize() {
        column("Ventanilla", w -> "" + w.id(), 90);
        column("Estado", w -> ManagedWindow.stateLabel(w.state()), 100);
        column("Modalidad", w -> ManagedWindow.modeLabel(w.mode()), 130);
        column("Asignación abierta", w -> w.openAssignmentId() == 0 ? "—" : "#" + w.openAssignmentId() + " · " + w.operatorName(), 200);
        column("Turno activo", w -> w.hasActiveTicket() ? "Sí" : "No", 100);
        column("Servicios activos", w -> "" + w.enabledServices(), 120);
        modeCombo.getItems().setAll(MIXED, PRIORITY_ONLY);
        windowsTable.getSelectionModel().selectedItemProperty().addListener((o, before, row) -> select(row));
        runner = new AdminTaskRunner(content, reloadButton, noticeLabel, "operatorsystem-windows");
        select(null);
        noticeLabel.setText("Pendiente de consulta.");
        content.setDisable(true);
    }

    public void configure(CatalogRequests requests, Consumer<String> sessionEnded) {
        this.requests = Objects.requireNonNull(requests);
        runner.onSessionEnded(sessionEnded);
        reload();
    }

    private void column(String title, Function<ManagedWindow, String> value, int width) {
        TableColumn<ManagedWindow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        windowsTable.getColumns().add(column);
    }

    @FXML
    private void reload() {
        load(0, "Ventanillas actualizadas. Seleccione una fila para editar o use Nueva ventanilla.");
    }

    private void load(int idToSelect, String success) {
        if (requests == null) return;
        runner.run(requests::windows, rows -> {
            windowsTable.getItems().setAll(rows);
            ManagedWindow match = rows.stream().filter(w -> w.id() == idToSelect).findFirst().orElse(null);
            if (match == null) newWindow();
            else windowsTable.getSelectionModel().select(match);
            noticeLabel.setText(success);
        }, false);
    }

    @FXML
    private void newWindow() {
        windowsTable.getSelectionModel().clearSelection();
        select(null);
    }

    private void select(ManagedWindow row) {
        selected = row;
        numberField.setText(row == null ? "" : "" + row.id());
        numberField.setDisable(row != null);
        modeCombo.setValue(row == null || "MIXED".equals(row.mode()) ? MIXED : PRIORITY_ONLY);
        // Con un turno activo el servidor rechazaría el cambio de modalidad; se adelanta en la interfaz.
        modeCombo.setDisable(row != null && row.hasActiveTicket());
        selectionLabel.setText(row == null ? "Nueva ventanilla" : "Ventanilla " + row.id());
        saveButton.setText(row == null ? "Crear ventanilla" : "Guardar modalidad");
        saveButton.setDisable(row != null && row.hasActiveTicket());
        stateButton.setVisible(row != null);
        stateButton.setManaged(row != null);
        if (row != null) {
            boolean inactive = "INACTIVA".equals(row.state());
            stateButton.setText(inactive ? "Reactivar ventanilla" : "Dar de baja");
            stateButton.setDisable(!inactive && row.openAssignmentId() != 0);
        }
        detailLabel.setText(row == null ? newWindowHelp() : details(row));
    }

    private static String newWindowHelp() {
        return "Use el número que se muestra al público (1 a 999); no se puede cambiar después. Se crea activa, sin servicios "
                + "habilitados ni asignación: configúrelos en Asignaciones. «Solo preferencial» nunca atiende turnos regulares.";
    }

    private static String details(ManagedWindow row) {
        StringBuilder text = new StringBuilder();
        switch (row.state()) {
            case "PAUSADA" -> text.append("En pausa temporal: la reanuda su operador desde Atención. No es una baja administrativa. ");
            case "INACTIVA" -> text.append("Dada de baja: no se puede asignar ni llamar turnos. Reactivarla conserva su historial y sus habilitaciones. ");
            default -> text.append("Activa. ");
        }
        if (row.openAssignmentId() != 0) {
            text.append("Tiene la asignación abierta #").append(row.openAssignmentId()).append(" con ").append(row.operatorName())
                    .append(". Para darla de baja, ciérrela primero en Asignaciones. ");
        }
        if (row.hasActiveTicket()) text.append("Tiene un turno llamado o en atención: la modalidad no se puede cambiar hasta finalizarlo. ");
        if (row.enabledServices() == 0) text.append("Sin servicios activos habilitados: configúrelos en Asignaciones.");
        else text.append("Servicios activos habilitados: ").append(row.enabledServices()).append('.');
        return text.toString();
    }

    @FXML
    private void save() {
        if (!runner.isIdle() || requests == null) return;
        ManagedWindow row = selected;
        ModeOption mode = modeCombo.getValue();
        if (mode == null) { noticeLabel.setText("Seleccione la modalidad."); return; }
        int id;
        if (row == null) {
            try {
                id = Integer.parseInt(numberField.getText().trim());
            } catch (NumberFormatException e) {
                id = 0;
            }
            if (id < 1 || id > 999) { noticeLabel.setText("Ingrese un número de ventanilla entre 1 y 999."); return; }
        } else {
            id = row.id();
            if (row.mode().equals(mode.value())) { noticeLabel.setText("No hay cambios para guardar."); return; }
        }
        String message = row == null
                ? "Crear la ventanilla " + id + " con modalidad " + ManagedWindow.modeLabel(mode.value()).toLowerCase() + "."
                : "Cambiar la ventanilla " + id + " a modalidad " + ManagedWindow.modeLabel(mode.value()).toLowerCase() + "."
                + ("PRIORITY_ONLY".equals(mode.value()) ? " Dejará de llamar turnos regulares." : "")
                + (row.openAssignmentId() != 0 ? " Su operador deberá usar Actualizar asignaciones antes de llamar." : "");
        if (!confirm(message)) return;
        int finalId = id;
        runner.run(() -> {
            if (row == null) requests.createWindow(finalId, mode.value());
            else requests.updateWindowMode(finalId, mode.value());
            return finalId;
        }, saved -> load(saved, row == null
                ? "Ventanilla " + saved + " creada. Habilite sus servicios y abra una asignación en Asignaciones."
                : "Modalidad de la ventanilla " + saved + " actualizada."), true);
    }

    @FXML
    private void toggleState() {
        if (!runner.isIdle() || requests == null || selected == null) return;
        ManagedWindow row = selected;
        boolean activate = "INACTIVA".equals(row.state());
        String message = activate
                ? "Reactivar la ventanilla " + row.id() + ". Quedará disponible para abrir asignaciones."
                : "Dar de baja la ventanilla " + row.id() + ". No se podrán abrir asignaciones ni llamar turnos en ella. "
                + "Su historial y habilitaciones se conservan.";
        if (!confirm(message)) return;
        runner.run(() -> requests.setWindowActive(row.id(), activate),
                state -> load(row.id(), "Ventanilla " + row.id() + (activate ? " reactivada." : " dada de baja.")), true);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(noticeLabel.getScene().getWindow());
        alert.setTitle("Administración de ventanillas");
        alert.setHeaderText("Confirmar cambio");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public void close() {
        runner.close();
    }
}
