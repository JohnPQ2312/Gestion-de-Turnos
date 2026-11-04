package cr.ac.una.QueueDisplayScreen.controller;

import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import cr.ac.una.QueueDisplayScreen.model.TicketRow;
import cr.ac.una.QueueDisplayScreen.service.DisplayRefreshService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;

public class DisplayScreenController {

    private static final DateTimeFormatter DATE_TIME
            = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter CLOCK
            = DateTimeFormatter.ofPattern("HH:mm:ss");

    private DisplayRefreshService refreshService;
    private Timeline clock;
    private LocalDateTime lastSuccessfulUpdate;
    private boolean closed;

    @FXML
    private Label clockLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label lastCalledLabel;
    @FXML
    private Label lastCallDetailsLabel;
    @FXML
    private TableView<TicketRow> ticketsTable;
    @FXML
    private TableColumn<TicketRow, String> codeColumn;
    @FXML
    private TableColumn<TicketRow, String> serviceColumn;
    @FXML
    private TableColumn<TicketRow, String> attentionColumn;
    @FXML
    private TableColumn<TicketRow, String> stateColumn;
    @FXML
    private TableView<TicketRow> recentCallsTable;
    @FXML
    private TableColumn<TicketRow, String> recentCodeColumn;
    @FXML
    private TableColumn<TicketRow, String> recentServiceColumn;
    @FXML
    private TableColumn<TicketRow, String> recentWindowColumn;
    @FXML
    private TableColumn<TicketRow, String> recentTimeColumn;
    @FXML
    private TableColumn<TicketRow, String> recentStateColumn;

    @FXML
    private void initialize() {
        setTextColumn(codeColumn, TicketRow::visualCode);
        setTextColumn(serviceColumn, TicketRow::serviceName);
        setTextColumn(attentionColumn, ticket -> ticket.priority() ? "Preferencial" : "Regular");
        setTextColumn(stateColumn, ticket -> stateName(ticket.state()));
        setTextColumn(recentCodeColumn, TicketRow::visualCode);
        setTextColumn(recentServiceColumn, TicketRow::serviceName);
        setTextColumn(recentWindowColumn, ticket -> String.valueOf(ticket.windowId()));
        setTextColumn(recentTimeColumn, ticket -> DATE_TIME.format(ticket.calledAt()));
        setTextColumn(recentStateColumn, ticket -> stateName(ticket.state()));
        clockLabel.setText(CLOCK.format(LocalDateTime.now()));
    }

    private void setTextColumn(
            TableColumn<TicketRow, String> column, Function<TicketRow, String> value
    ) {
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
    }

    public void configure(DisplayRefreshService.SnapshotRequest request) {
        Objects.requireNonNull(request, "La consulta de la pantalla es obligatoria.");
        if (closed || refreshService != null) {
            throw new IllegalStateException("La pantalla ya fue configurada o está cerrada.");
        }

        refreshService = new DisplayRefreshService(
                request,
                snapshot -> Platform.runLater(() -> {
                    if (!closed) {
                        showSnapshot(snapshot);
                    }
                }),
                error -> Platform.runLater(() -> {
                    if (!closed) {
                        showFailure(error);
                    }
                })
        );

        clock = new Timeline(new KeyFrame(Duration.seconds(1), event
                -> clockLabel.setText(CLOCK.format(LocalDateTime.now()))));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        refreshService.start();
    }

    private void showSnapshot(DisplaySnapshot snapshot) {
        ticketsTable.getItems().setAll(snapshot.waitingTickets());
        recentCallsTable.getItems().setAll(snapshot.recentCalls());
        ticketsTable.setPlaceholder(new Label("No hay turnos en espera."));
        recentCallsTable.setPlaceholder(new Label("Todavía no hay llamados registrados."));

        if (snapshot.recentCalls().isEmpty()) {
            lastCalledLabel.setText("Todavía no hay llamados registrados.");
            lastCallDetailsLabel.setText("");
        } else {
            TicketRow last = snapshot.recentCalls().get(0);
            lastCalledLabel.setText("Último llamado: " + last.visualCode()
                    + " → Ventanilla " + last.windowId());
            lastCallDetailsLabel.setText(last.serviceName()
                    + " | Llamado: " + DATE_TIME.format(last.calledAt())
                    + "\nEstado actual: " + stateName(last.state()));
        }

        lastSuccessfulUpdate = LocalDateTime.now();
        connectionLabel.setText("Conectado. Última actualización: "
                + DATE_TIME.format(lastSuccessfulUpdate));
        connectionLabel.setStyle("-fx-text-fill: #216e39;");
    }

    private void showFailure(Exception error) {
        System.err.println("No se pudo actualizar la pantalla pública. Detalle: " + error);
        String message = lastSuccessfulUpdate == null
                ? "Sin datos recibidos. Reintentando la conexión automáticamente."
                : "Datos sin actualizar desde " + DATE_TIME.format(lastSuccessfulUpdate)
                + ". Reintentando la conexión automáticamente.";
        connectionLabel.setText(message);
        connectionLabel.setStyle("-fx-text-fill: #a45a00; -fx-font-weight: bold;");
    }

    private String stateName(String state) {
        return switch (state) {
            case "EN_ESPERA" ->
                "En espera";
            case "LLAMADO" ->
                "Llamado";
            case "EN_ATENCION" ->
                "En atención";
            case "FINALIZADO" ->
                "Finalizado";
            default ->
                state;
        };
    }

    public void close() {
        closed = true;
        if (clock != null) {
            clock.stop();
        }
        if (refreshService != null) {
            refreshService.close();
        }
    }
}
