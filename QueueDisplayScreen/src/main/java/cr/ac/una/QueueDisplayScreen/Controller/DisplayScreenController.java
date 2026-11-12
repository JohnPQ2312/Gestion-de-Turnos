package cr.ac.una.QueueDisplayScreen.controller;

import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import cr.ac.una.QueueDisplayScreen.model.TicketRow;
import cr.ac.una.QueueDisplayScreen.service.DisplayRefreshService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.scene.layout.HBox;
import java.util.Map;
import java.util.HashMap;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.css.PseudoClass;
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
    private boolean snapshotReceived;
    private Map<Long, Integer> knownCalls = Map.of();
    private final PauseTransition recallHighlight = new PauseTransition(Duration.seconds(4));
    @FXML private HBox lastCallPanel;
    @FXML private Label callNoticeLabel;

    @FXML
    private Label clockLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label lastCalledLabel;
    @FXML
    private Label lastWindowLabel;
    @FXML
    private Label lastStateLabel;
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
        setTextColumn(recentTimeColumn, ticket -> DATE_TIME.format(ticket.lastCalledAt()));
        setTextColumn(recentStateColumn, ticket -> stateName(ticket.state()));
        ticketsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        recentCallsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ticketsTable.setSelectionModel(null);
        recentCallsTable.setSelectionModel(null);
        clockLabel.setText(CLOCK.format(LocalDateTime.now()));
    }

    private void setTextColumn(
            TableColumn<TicketRow, String> column, Function<TicketRow, String> value
    ) {
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
    }

    public void configure(
            DisplayRefreshService.SnapshotRequest request,
            DisplayRefreshService.SubscriptionRequest subscriptions
    ) {
        Objects.requireNonNull(request, "La consulta de la pantalla es obligatoria.");
        Objects.requireNonNull(subscriptions, "La suscripción de la pantalla es obligatoria.");
        if (closed || refreshService != null) {
            throw new IllegalStateException("La pantalla ya fue configurada o está cerrada.");
        }

        refreshService = new DisplayRefreshService(
                request,
                subscriptions,
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
            recallHighlight.stop();
            lastCallPanel.pseudoClassStateChanged(PseudoClass.getPseudoClass("recalled"), false);
            callNoticeLabel.setVisible(false);
            callNoticeLabel.setManaged(false);
            lastCalledLabel.setText("—");
            lastCalledLabel.setStyle("-fx-font-size: 72px;");
            lastWindowLabel.setText("—");
            lastStateLabel.setText("");
            lastCallDetailsLabel.setText("Los turnos llamados aparecerán aquí.");
        } else {
            TicketRow last = snapshot.recentCalls().get(0);
            lastCalledLabel.setText(last.visualCode());
            int fontSize = last.visualCode().length() > 12 ? 28
                    : last.visualCode().length() > 8 ? 48
                    : last.visualCode().length() > 6 ? 72 : 104;
            lastCalledLabel.setStyle("-fx-font-size: " + fontSize + "px;");
            lastWindowLabel.setText(String.valueOf(last.windowId()));
            lastStateLabel.setText(stateName(last.state()));
            lastCallDetailsLabel.setText(last.serviceName()
                    + (last.callCount() > 1 ? "\nPrimer llamado: " : "\nLlamado: ")
                    + DATE_TIME.format(last.calledAt())
                    + (last.callCount() > 1 ? "\nÚltimo llamado: "
                            + DATE_TIME.format(last.lastCalledAt()) : ""));
            boolean recalled = last.callCount() > 1;
            callNoticeLabel.setText(recalled && "LLAMADO".equals(last.state())
                    ? "Volvemos a llamar · Llamado " + last.callCount()
                    : "Llamados: " + last.callCount());
            callNoticeLabel.setVisible(recalled);
            callNoticeLabel.setManaged(recalled);
            Integer previous = knownCalls.get(last.ticketId());
            boolean newRecall = snapshotReceived && recalled && "LLAMADO".equals(last.state())
                    && (previous == null || last.callCount() > previous);
            if (newRecall) {
                recallHighlight.stop();
                lastCallPanel.pseudoClassStateChanged(PseudoClass.getPseudoClass("recalled"), true);
                recallHighlight.setOnFinished(event -> lastCallPanel.pseudoClassStateChanged(
                        PseudoClass.getPseudoClass("recalled"), false));
                recallHighlight.playFromStart();
            } else if (!recalled || !"LLAMADO".equals(last.state())) {
                recallHighlight.stop();
                lastCallPanel.pseudoClassStateChanged(PseudoClass.getPseudoClass("recalled"), false);
            }
        }

        Map<Long, Integer> updatedCalls = new HashMap<>();
        for (TicketRow row : snapshot.recentCalls()) {
            updatedCalls.put(row.ticketId(), row.callCount());
        }
        knownCalls = Map.copyOf(updatedCalls);
        snapshotReceived = true;
        lastSuccessfulUpdate = LocalDateTime.now();
        connectionLabel.setText("Conectado. Última actualización: "
                + DATE_TIME.format(lastSuccessfulUpdate));
        connectionLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("offline"), false);
    }

    private void showFailure(Exception error) {
        System.err.println("No se pudo actualizar la pantalla pública. Detalle: " + error);
        String message = lastSuccessfulUpdate == null
                ? "Sin datos recibidos. Reintentando la conexión automáticamente."
                : "Datos sin actualizar desde " + DATE_TIME.format(lastSuccessfulUpdate)
                + ". Reintentando la conexión automáticamente.";
        connectionLabel.setText(message);
        connectionLabel.pseudoClassStateChanged(PseudoClass.getPseudoClass("offline"), true);
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
        recallHighlight.stop();
        if (clock != null) {
            clock.stop();
        }
        if (refreshService != null) {
            refreshService.close();
        }
    }
}
