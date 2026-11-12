package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.model.ReportResult;
import cr.ac.una.operatorsystem.model.ReportType;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Vista de las cinco consultas de la sección 13.
 *
 * Recoge la consulta y el rango, pide los datos al servidor en un hilo
 * virtual y actualiza la tabla en el hilo de JavaFX (los manejadores de Task
 * se ejecutan en ese hilo). No contiene SQL ni reglas de cálculo: el servidor
 * calcula y el cliente solo da formato.
 */
public class ReportsController {

    @FunctionalInterface
    public interface ReportRequest {

        ReportResult fetch(ReportType type, LocalDate from, LocalDate to) throws IOException;
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private ComboBox<ReportType> reportCombo;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private Button queryButton;
    @FXML
    private Label criterionLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private TableView<List<String>> resultsTable;
    @FXML
    private Label summaryLabel;

    private ReportRequest reportRequest;
    private Task<ReportResult> currentTask;
    private boolean busy;
    private boolean closed;

    @FXML
    private void initialize() {
        reportCombo.getItems().setAll(ReportType.values());
        reportCombo.getSelectionModel().selectFirst();
        reportCombo.valueProperty().addListener((observable, previous, selected) -> showCriterion());

        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today);

        showCriterion();
        hideError();
        summaryLabel.setText("Elija una consulta y un rango de fechas, y presione Consultar.");
        updateControls();
    }

    public void configure(ReportRequest reportRequest) {
        if (busy || closed) {
            throw new IllegalStateException("La vista de consultas no se puede configurar en este momento.");
        }
        this.reportRequest = Objects.requireNonNull(
                reportRequest, "La solicitud de consultas es obligatoria."
        );
        updateControls();
    }

    @FXML
    private void runReport() {
        if (busy || closed || reportRequest == null) {
            return;
        }

        ReportType type = reportCombo.getValue();
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        if (type == null) {
            showError("Seleccione una consulta.");
            return;
        }
        if (from == null || to == null) {
            showError("Seleccione la fecha inicial y la fecha final.");
            return;
        }
        if (from.isAfter(to)) {
            showError("La fecha inicial no puede ser posterior a la fecha final.");
            return;
        }

        hideError();
        summaryLabel.setText("Consultando...");

        Task<ReportResult> task = new Task<>() {
            @Override
            protected ReportResult call() throws IOException {
                return reportRequest.fetch(type, from, to);
            }
        };

        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            showResult(type, from, to, task.getValue());
            setBusy(false);
        });

        task.setOnFailed(event -> {
            if (closed) {
                return;
            }
            Throwable error = task.getException();
            System.err.println("No se pudo obtener la consulta " + type.command() + ". Detalle: " + error);
            clearResult();
            summaryLabel.setText("");
            // IllegalArgumentException = el servidor rechazó la solicitud con
            // un mensaje para el usuario (por ejemplo, un rango inválido).
            showError(error instanceof IllegalArgumentException && error.getMessage() != null
                    ? error.getMessage()
                    : "No se pudo obtener la consulta. Compruebe que CoreServer esté disponible "
                    + "y vuelva a intentarlo.");
            setBusy(false);
        });

        currentTask = task;
        setBusy(true);
        Thread.ofVirtual().name("operatorsystem-report").start(task);
    }

    private void showResult(ReportType type, LocalDate from, LocalDate to, ReportResult result) {
        resultsTable.getColumns().clear();

        for (int index = 0; index < result.columns().size(); index++) {
            final int columnIndex = index;
            TableColumn<List<String>, String> column = new TableColumn<>(result.columns().get(index));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(columnIndex)));
            column.setSortable(false);
            resultsTable.getColumns().add(column);
        }

        resultsTable.getItems().setAll(result.rows());

        String range = "del " + from.format(DATE) + " al " + to.format(DATE);
        summaryLabel.setText(result.rows().isEmpty()
                ? type.title() + ", " + range + ": sin resultados."
                : type.title() + ", " + range + ". " + result.summary());
    }

    private void clearResult() {
        resultsTable.getItems().clear();
        resultsTable.getColumns().clear();
    }

    private void showCriterion() {
        ReportType type = reportCombo.getValue();
        criterionLabel.setText(type == null ? "" : "Criterio: " + type.criterion());
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    private void updateControls() {
        boolean blocked = closed || busy;
        reportCombo.setDisable(blocked);
        fromDatePicker.setDisable(blocked);
        toDatePicker.setDisable(blocked);
        queryButton.setDisable(blocked || reportRequest == null);
        queryButton.setText(busy ? "Consultando..." : "Consultar");
    }

    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        updateControls();
    }
}
