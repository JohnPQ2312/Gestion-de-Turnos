package cr.ac.una.operatorsystem.controller;

import cr.ac.una.operatorsystem.client.AccessDeniedException;
import cr.ac.una.operatorsystem.model.ReportResult;
import cr.ac.una.operatorsystem.model.ReportType;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

// Recoge el tipo de consulta y el período y pide el reporte en un hilo virtual.
// Task entrega los resultados a JavaFX; la vista presenta los cálculos hechos por CoreServer.
public class ReportsController {

    @FunctionalInterface
    public interface ReportRequest {

        ReportResult fetch(ReportType type, LocalDate from, LocalDate to) throws IOException;
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Medida aproximada del texto de la tabla (operator.css usa Segoe UI 16 px).
    // Se mide en negrita para no quedarse corto con los encabezados.
    private static final Font MEASURE_FONT = Font.font("Segoe UI", FontWeight.BOLD, 16);
    private static final double CELL_PADDING = 32;
    private static final double MIN_COLUMN_WIDTH = 70;
    private static final double MAX_COLUMN_WIDTH = 420;

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
    private Consumer<String> sessionEndedHandler;
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

    public void configure(ReportRequest reportRequest, Consumer<String> sessionEndedHandler) {
        if (busy || closed) {
            throw new IllegalStateException("La vista de consultas no se puede configurar en este momento.");
        }
        this.reportRequest = Objects.requireNonNull(
                reportRequest, "La solicitud de consultas es obligatoria."
        );
        this.sessionEndedHandler = Objects.requireNonNull(
                sessionEndedHandler, "El aviso de fin de sesión es obligatorio."
        );
        updateControls();
    }

    // Valida la consulta y las fechas y ejecuta la solicitud fuera del hilo de JavaFX.
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

            if (error instanceof AccessDeniedException denied) {
                setBusy(false);
                showError(denied.getMessage());
                if (denied.endsSession()) {
                    sessionEndedHandler.accept(denied.getMessage());
                }
                return;
            }

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
            String header = result.columns().get(index);
            TableColumn<List<String>, String> column = new TableColumn<>(header);
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(columnIndex)));
            column.setCellFactory(ignored -> new TooltipCell());
            column.setPrefWidth(columnWidth(header, result.rows(), columnIndex));
            column.setSortable(false);
            resultsTable.getColumns().add(column);
        }

        resultsTable.getItems().setAll(result.rows());

        String range = "del " + from.format(DATE) + " al " + to.format(DATE);
        summaryLabel.setText(result.rows().isEmpty()
                ? type.title() + ", " + range + ": sin resultados."
                : type.title() + ", " + range + ". " + result.summary());
    }

    // Ancho de una columna según su encabezado y su valor más largo. La
    // tabla no reparte el ancho de la ventana entre las columnas: si no caben,
    // muestra desplazamiento horizontal en lugar de recortar los textos.
    private static double columnWidth(String header, List<List<String>> rows, int columnIndex) {
        double width = textWidth(header);
        for (List<String> row : rows) {
            width = Math.max(width, textWidth(row.get(columnIndex)));
        }
        return Math.min(MAX_COLUMN_WIDTH, Math.max(MIN_COLUMN_WIDTH, width + CELL_PADDING));
    }

    private static double textWidth(String value) {
        Text text = new Text(value);
        text.setFont(MEASURE_FONT);
        return text.getLayoutBounds().getWidth();
    }

    // Celda que muestra su texto completo al pasar el cursor, por si aún se recorta.
    private static final class TooltipCell extends TableCell<List<String>, String> {

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            boolean blank = empty || item == null || item.isEmpty();
            setText(blank ? null : item);
            setTooltip(blank ? null : new Tooltip(item));
        }
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
