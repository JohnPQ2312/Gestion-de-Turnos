package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.CatalogRequests;
import cr.ac.una.operatorsystem.model.ManagedService;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

// Administra el formulario de servicios y anticipa errores de formato al guardar.
// CoreServer comprueba los duplicados, el prefijo y las dependencias del servicio.
public class ServicesController {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,19}");
    private static final Pattern PREFIX = Pattern.compile("[A-Z]{1,5}");

    @FXML private BorderPane content;
    @FXML private TableView<ManagedService> servicesTable;
    @FXML private Label selectionLabel;
    @FXML private TextField codeField;
    @FXML private TextField nameField;
    @FXML private TextField prefixField;
    @FXML private Label detailLabel;
    @FXML private Button saveButton;
    @FXML private Button stateButton;
    @FXML private Button reloadButton;
    @FXML private Label noticeLabel;

    private CatalogRequests requests;
    private AdminTaskRunner runner;
    private ManagedService selected;

    @FXML
    private void initialize() {
        column("Código", ManagedService::code, 100);
        column("Nombre", ManagedService::name, 200);
        column("Prefijo", ManagedService::prefix, 70);
        column("Estado", s -> s.active() ? "Activo" : "Inactivo", 80);
        column("Emitidos", s -> "" + s.issuedTickets(), 80);
        column("Pendientes", s -> "" + s.pendingTickets(), 90);
        column("Rutas abiertas", s -> "" + s.openRoutes(), 110);
        servicesTable.getSelectionModel().selectedItemProperty().addListener((o, before, row) -> select(row));
        runner = new AdminTaskRunner(content, reloadButton, noticeLabel, "operatorsystem-services");
        select(null);
        noticeLabel.setText("Pendiente de consulta.");
        content.setDisable(true);
    }

    public void configure(CatalogRequests requests, Consumer<String> sessionEnded) {
        this.requests = Objects.requireNonNull(requests);
        runner.onSessionEnded(sessionEnded);
        reload();
    }

    private void column(String title, Function<ManagedService, String> value, int width) {
        TableColumn<ManagedService, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        servicesTable.getColumns().add(column);
    }

    @FXML
    private void reload() {
        load(null, "Servicios actualizados. Seleccione una fila para editar o use Nuevo servicio.");
    }

    // Recarga el listado y, si se indica, vuelve a seleccionar el servicio guardado.
    private void load(String codeToSelect, String success) {
        if (requests == null) return;
        runner.run(requests::services, rows -> {
            servicesTable.getItems().setAll(rows);
            ManagedService match = rows.stream().filter(s -> s.code().equals(codeToSelect)).findFirst().orElse(null);
            if (match == null) newService();
            else servicesTable.getSelectionModel().select(match);
            noticeLabel.setText(success);
        }, false);
    }

    @FXML
    private void newService() {
        servicesTable.getSelectionModel().clearSelection();
        select(null);
    }

    private void select(ManagedService row) {
        selected = row;
        codeField.setText(row == null ? "" : row.code());
        codeField.setDisable(row != null);
        nameField.setText(row == null ? "" : row.name());
        prefixField.setText(row == null ? "" : row.prefix());
        prefixField.setDisable(row != null && row.prefixLocked());
        selectionLabel.setText(row == null ? "Nuevo servicio" : "Servicio " + row.code());
        saveButton.setText(row == null ? "Crear servicio" : "Guardar cambios");
        stateButton.setVisible(row != null);
        stateButton.setManaged(row != null);
        if (row != null) stateButton.setText(row.active() ? "Desactivar servicio" : "Reactivar servicio");
        detailLabel.setText(row == null ? newServiceHelp() : details(row));
    }

    private static String newServiceHelp() {
        return "El código identifica al servicio y no se puede cambiar después. El prefijo forma los turnos (B + 001 = B001). "
                + "Se crea activo con su consecutivo en 0; no se habilita automáticamente en funcionarios ni ventanillas: "
                + "configure su atención en Asignaciones.";
    }

    private static String details(ManagedService row) {
        StringBuilder text = new StringBuilder("Turnos emitidos: " + row.issuedTickets()
                + ". Pendientes (en espera, llamados o en atención): " + row.pendingTickets()
                + ". Asignaciones abiertas que lo atienden: " + row.openRoutes() + ".");
        if (row.prefixLocked()) text.append(" El prefijo está protegido porque el servicio ya emitió turnos.");
        if (row.active() && row.openRoutes() == 0) {
            text.append(" Sin ruta de atención: los turnos de este servicio quedarán en espera hasta configurarlo en Asignaciones.");
        }
        if (!row.active()) text.append(" Inactivo: no aparece en el generador de turnos. Reactivarlo conserva su numeración.");
        return text.toString();
    }

    @FXML
    private void save() {
        if (!runner.isIdle() || requests == null) return;
        ManagedService row = selected;
        String code = row == null ? codeField.getText().trim().toUpperCase(Locale.ROOT) : row.code();
        String name = nameField.getText().trim();
        String prefix = prefixField.getText().trim();
        // Un prefijo heredado sin cambios se envía tal como está guardado.
        if (row == null || !prefix.equals(row.prefix())) prefix = prefix.toUpperCase(Locale.ROOT);

        if (row == null && !CODE.matcher(code).matches()) {
            noticeLabel.setText("Código no válido: de 1 a 20 caracteres, inicia con letra y usa letras sin tildes, dígitos o guion bajo.");
            return;
        }
        if (name.isEmpty() || name.length() > 80) {
            noticeLabel.setText("El nombre es obligatorio y admite hasta 80 caracteres.");
            return;
        }
        boolean prefixChanged = row == null || !prefix.equals(row.prefix());
        if (prefixChanged && !PREFIX.matcher(prefix).matches()) {
            noticeLabel.setText("Prefijo no válido: de 1 a 5 letras sin tildes, sin dígitos ni espacios.");
            return;
        }
        if (row != null && !prefixChanged && name.equals(row.name())) {
            noticeLabel.setText("No hay cambios para guardar.");
            return;
        }
        String message = row == null
                ? "Crear el servicio " + code + " (" + name + ") con prefijo " + prefix + ". Quedará activo, sin funcionarios ni ventanillas habilitados."
                : "Guardar los cambios del servicio " + code + "." + (prefixChanged ? " El prefijo pasará a " + prefix + "." : "");
        if (!confirm(message)) return;
        String finalPrefix = prefix;
        runner.run(() -> {
            if (row == null) requests.createService(code, name, finalPrefix);
            else requests.updateService(code, name, finalPrefix);
            return code;
        }, saved -> load(saved, row == null
                ? "Servicio " + saved + " creado. Configure sus habilitaciones y asignaciones en Asignaciones."
                : "Servicio " + saved + " actualizado."), true);
    }

    @FXML
    private void toggleState() {
        if (!runner.isIdle() || requests == null || selected == null) return;
        ManagedService row = selected;
        boolean activate = !row.active();
        String message = activate
                ? "Reactivar el servicio " + row.code() + ". Volverá a aparecer en el generador y conservará su numeración."
                : "Desactivar el servicio " + row.code() + ". Dejará de aparecer en el generador. Se rechaza si tiene turnos "
                + "pendientes o si deja una asignación abierta sin servicios en común.";
        if (!confirm(message)) return;
        runner.run(() -> { requests.setServiceActive(row.code(), activate); return row.code(); },
                saved -> load(saved, "Servicio " + saved + (activate ? " reactivado." : " desactivado.")), true);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(noticeLabel.getScene().getWindow());
        alert.setTitle("Administración de servicios");
        alert.setHeaderText("Confirmar cambio");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public void close() {
        runner.close();
    }
}
