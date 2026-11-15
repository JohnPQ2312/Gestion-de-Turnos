package cr.ac.una.QueueDisplayScreen.controller;

import cr.ac.una.QueueDisplayScreen.model.DisplaySnapshot;
import cr.ac.una.QueueDisplayScreen.model.TicketRow;
import cr.ac.una.QueueDisplayScreen.service.DisplayRefreshService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

// Presenta el turno llamado vigente arriba, la lista de turnos activos debajo y el estado de
// conexión. Los finalizados no llegan a la pantalla: la captura V3 solo trae LLAMADO,
// EN_ATENCION y EN_ESPERA. Cada fila se conserva por identificador y se actualiza al cambiar de
// estado. Si las filas no caben, se pagina automáticamente para que ningún activo quede oculto.
// Los códigos y ventanillas se miden para mostrarse completos en una línea, sin puntos
// suspensivos: se ajusta el ancho de su columna y, si no alcanza, el tamaño de letra.
public class DisplayScreenController {

    private static final DateTimeFormatter CLOCK
            = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String FONT_FAMILY = "Segoe UI";

    // Lista: alto de fila y tamaños usados tanto en el CSS como en la medición de columnas.
    private static final double ROW_HEIGHT = 44;
    private static final double ROW_CODE_FONT = 26;
    private static final double ROW_CODE_MIN_FONT = 15;
    private static final double ROW_TEXT_FONT = 18;
    private static final double ROW_WINDOW_FONT = 24;
    private static final double HEADER_FONT = 15;
    private static final double CELL_PADDING = 24;
    private static final double MEASURE_SLACK = 6;
    private static final double MIN_SERVICE_WIDTH = 160;
    // Ancho de los bordes izquierdos que comparten encabezado y filas (CSS).
    private static final double ROW_INSETS = 8;

    // Anuncio: tamaño máximo del código y la ventanilla; el alto queda fijo para que la franja
    // no cambie de tamaño entre turnos.
    private static final double ANNOUNCE_MAX_FONT = 84;
    private static final double ANNOUNCE_MIN_FONT = 26;
    private static final double ANNOUNCE_IDLE_FONT = 36;
    private static final double ANNOUNCE_LINE_HEIGHT = 108;

    private static final String[] STATE_TEXTS = {
        "Rellamado · Ahora", "Llamado · Ahora", "Rellamado", "Llamado", "En atención", "En espera"
    };

    // Cuando los llamados caben, se fijan en todas las páginas y la espera ocupa el resto.
    private static final int MIN_WAITING_SLOTS = 2;
    private static final Duration PAGE_INTERVAL = Duration.seconds(8);
    private static final PseudoClass RECALLED = PseudoClass.getPseudoClass("recalled");
    private static final PseudoClass OFFLINE = PseudoClass.getPseudoClass("offline");
    private static final PseudoClass IDLE = PseudoClass.getPseudoClass("idle");
    private static final PseudoClass CALLED = PseudoClass.getPseudoClass("called");
    private static final PseudoClass ATTENTION = PseudoClass.getPseudoClass("attention");
    private static final PseudoClass WAITING = PseudoClass.getPseudoClass("waiting");
    private static final PseudoClass LATEST = PseudoClass.getPseudoClass("latest");
    private static final PseudoClass ODD = PseudoClass.getPseudoClass("odd");

    private DisplayRefreshService refreshService;
    private Timeline clock;
    private Timeline pager;
    private LocalDateTime lastSuccessfulUpdate;
    private boolean closed;
    private boolean snapshotReceived;
    // Contador de llamados por ticket en la captura anterior; sirve para destacar solo
    // rellamados realmente nuevos.
    private Map<Long, Integer> knownCalls = Map.of();
    private Long announcedId;
    private boolean announcing;
    private final PauseTransition recallHighlight = new PauseTransition(Duration.seconds(4));

    private final Map<Long, ActiveRow> rowsById = new HashMap<>();
    private List<ActiveRow> calledRows = List.of();
    private List<ActiveRow> waitingRows = List.of();
    private int pageIndex;
    private int pageCount;
    private final Label emptyRowsLabel = new Label("Esperando información del servidor.");

    @FXML private GridPane lastCallPanel;
    @FXML private VBox codeBlock;
    @FXML private VBox windowBlock;
    @FXML private Label callNoticeLabel;
    @FXML private Label clockLabel;
    @FXML private Label connectionLabel;
    @FXML private Label lastCalledLabel;
    @FXML private Label lastWindowLabel;
    @FXML private Label lastStateLabel;
    @FXML private Label lastCallDetailsLabel;
    @FXML private GridPane listHeader;
    @FXML private VBox rowsBox;
    @FXML private Label pageLabel;
    @FXML private Label listNoteLabel;

    @FXML
    private void initialize() {
        configureColumns(listHeader);
        addCell(listHeader, "Turno", 0, "header-cell", false);
        addCell(listHeader, "Servicio", 1, "header-cell", true);
        addCell(listHeader, "Estado", 2, "header-cell", false);
        addCell(listHeader, "Ventanilla", 3, "header-cell", false);

        emptyRowsLabel.getStyleClass().add("placeholder-text");
        emptyRowsLabel.setWrapText(true);
        emptyRowsLabel.setMaxWidth(Double.MAX_VALUE);

        // Código y ventanilla del anuncio: una sola línea, sin recorte, con alto fijo.
        for (Label label : List.of(lastCalledLabel, lastWindowLabel)) {
            label.setWrapText(false);
            label.setMinWidth(Region.USE_PREF_SIZE);
            label.setMinHeight(ANNOUNCE_LINE_HEIGHT);
            label.setPrefHeight(ANNOUNCE_LINE_HEIGHT);
            label.setMaxHeight(ANNOUNCE_LINE_HEIGHT);
        }
        codeBlock.widthProperty().addListener((observable, oldValue, newValue) -> fitAnnouncement());
        windowBlock.widthProperty().addListener((observable, oldValue, newValue) -> fitAnnouncement());

        // La lista no se desplaza: se recorta a su área y se recalculan las páginas al cambiar
        // el alto disponible, y las columnas al cambiar el ancho.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(rowsBox.widthProperty());
        clip.heightProperty().bind(rowsBox.heightProperty());
        rowsBox.setClip(clip);
        rowsBox.heightProperty().addListener((observable, oldValue, newValue) -> renderPage());
        rowsBox.widthProperty().addListener((observable, oldValue, newValue) -> updateColumns());

        showNoAnnouncement();
        hideNote();
        updateColumns();
        renderPage();
        clockLabel.setText(CLOCK.format(LocalDateTime.now()));
    }

    // Las respuestas de red pasan por Platform.runLater antes de tocar controles JavaFX.
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

        pager = new Timeline(new KeyFrame(PAGE_INTERVAL, event -> nextPage()));
        pager.setCycleCount(Timeline.INDEFINITE);
        pager.play();

        refreshService.start();
    }

    // Aplica una captura: anuncio, filas por identificador, nota de espera parcial y conexión.
    private void showSnapshot(DisplaySnapshot snapshot) {
        // calledTickets llega ordenado por último llamado (el más reciente primero), de modo que
        // el primero es el anuncio vigente aunque ya esté en atención.
        TicketRow announced = snapshot.calledTickets().isEmpty()
                ? null : snapshot.calledTickets().get(0);

        if (announced == null) {
            showNoAnnouncement();
        } else {
            showAnnouncement(announced);
        }

        updateRows(snapshot, announced);
        updateNote(snapshot);

        Map<Long, Integer> updatedCalls = new HashMap<>();
        for (TicketRow row : snapshot.calledTickets()) {
            updatedCalls.put(row.ticketId(), row.callCount());
        }
        knownCalls = Map.copyOf(updatedCalls);
        snapshotReceived = true;
        lastSuccessfulUpdate = LocalDateTime.now();
        connectionLabel.setText("Conectado · Actualizado a las "
                + CLOCK.format(lastSuccessfulUpdate));
        connectionLabel.pseudoClassStateChanged(OFFLINE, false);
    }

    // El anuncio es el turno llamado o en atención con el último llamado más reciente. Se mantiene
    // mientras esté en atención y lo sustituye solo otro llamado más reciente. El aviso de
    // rellamado corresponde únicamente a un turno que sigue en estado LLAMADO.
    private void showAnnouncement(TicketRow last) {
        announcing = true;
        boolean called = "LLAMADO".equals(last.state());
        lastCallPanel.pseudoClassStateChanged(IDLE, false);
        lastCalledLabel.setText(last.visualCode());
        lastWindowLabel.setText(String.valueOf(last.windowId()));
        lastCallDetailsLabel.setText(last.serviceName()
                + (last.priority() ? " · Preferencial" : ""));
        lastStateLabel.setText(called
                ? "Diríjase a la ventanilla " + last.windowId()
                : "Está siendo atendido en la ventanilla " + last.windowId());
        fitAnnouncement();

        boolean recalled = called && last.callCount() > 1;
        callNoticeLabel.setText("Volvemos a llamar");
        callNoticeLabel.setVisible(recalled);
        callNoticeLabel.setManaged(recalled);

        // La primera captura carga el estado sin anunciar un rellamado nuevo. Después solo se
        // destaca si el contador persistido aumentó respecto a la captura anterior.
        Integer previous = knownCalls.get(last.ticketId());
        boolean newRecall = snapshotReceived && recalled
                && (previous == null || last.callCount() > previous);
        if (newRecall) {
            recallHighlight.stop();
            lastCallPanel.pseudoClassStateChanged(RECALLED, true);
            recallHighlight.setOnFinished(event
                    -> lastCallPanel.pseudoClassStateChanged(RECALLED, false));
            recallHighlight.playFromStart();
        } else if (!recalled || !Long.valueOf(last.ticketId()).equals(announcedId)) {
            stopRecallHighlight();
        }
        announcedId = last.ticketId();
    }

    // Mensaje neutral cuando no hay turnos llamados ni en atención: no conserva un finalizado.
    private void showNoAnnouncement() {
        announcing = false;
        announcedId = null;
        stopRecallHighlight();
        lastCallPanel.pseudoClassStateChanged(IDLE, true);
        callNoticeLabel.setVisible(false);
        callNoticeLabel.setManaged(false);
        lastCalledLabel.setText("Sin llamados pendientes");
        lastWindowLabel.setText("—");
        lastCallDetailsLabel.setText("El próximo turno llamado aparecerá aquí.");
        lastStateLabel.setText("");
        fitAnnouncement();
    }

    private void stopRecallHighlight() {
        recallHighlight.stop();
        lastCallPanel.pseudoClassStateChanged(RECALLED, false);
    }

    // Ajusta el código y la ventanilla del anuncio al ancho de su bloque, en una sola línea.
    private void fitAnnouncement() {
        double codeMax = announcing ? ANNOUNCE_MAX_FONT : ANNOUNCE_IDLE_FONT;
        double windowMax = announcing ? ANNOUNCE_MAX_FONT : ANNOUNCE_IDLE_FONT;
        setFontSize(lastCalledLabel, fitSize(lastCalledLabel.getText(),
                codeBlock.getWidth() - MEASURE_SLACK, codeMax, ANNOUNCE_MIN_FONT));
        setFontSize(lastWindowLabel, fitSize(lastWindowLabel.getText(),
                windowBlock.getWidth() - MEASURE_SLACK, windowMax, ANNOUNCE_MIN_FONT));
    }

    // Reutiliza la fila de cada ticket conocido y descarta las que ya no están activas
    // (por ejemplo, al finalizar). El orden es: llamados/en atención por último llamado y
    // después la espera en el orden de atención que envía el servidor.
    private void updateRows(DisplaySnapshot snapshot, TicketRow announced) {
        Set<Long> present = new HashSet<>();
        List<ActiveRow> called = new ArrayList<>();
        List<ActiveRow> waiting = new ArrayList<>();

        for (TicketRow ticket : snapshot.calledTickets()) {
            called.add(rowFor(ticket, announced, present));
        }
        for (TicketRow ticket : snapshot.waitingTickets()) {
            waiting.add(rowFor(ticket, announced, present));
        }
        rowsById.keySet().retainAll(present);

        calledRows = List.copyOf(called);
        waitingRows = List.copyOf(waiting);
        emptyRowsLabel.setText("No hay turnos activos en este momento.");
        updateColumns();
        renderPage();
    }

    private ActiveRow rowFor(TicketRow ticket, TicketRow announced, Set<Long> present) {
        present.add(ticket.ticketId());
        ActiveRow row = rowsById.computeIfAbsent(ticket.ticketId(), id -> new ActiveRow());
        row.update(ticket, announced != null && announced.ticketId() == ticket.ticketId());
        return row;
    }

    // Calcula columnas comunes para encabezado y filas. Turno, Estado y Ventanilla toman el
    // ancho de su texto más largo; Servicio usa el resto. Si el código más largo no cabe
    // dejando un mínimo para el servicio, se reduce su letra en lugar de recortarlo.
    private void updateColumns() {
        double available = rowsBox.getWidth() - ROW_INSETS;
        double extra = CELL_PADDING + MEASURE_SLACK;

        String longestCode = "";
        double longestCodeWidth = 0;
        double windowText = textWidth("Ventanilla", HEADER_FONT);
        for (ActiveRow row : rowsById.values()) {
            double width = textWidth(row.code.getText(), ROW_CODE_FONT);
            if (width > longestCodeWidth) {
                longestCodeWidth = width;
                longestCode = row.code.getText();
            }
            windowText = Math.max(windowText, textWidth(row.window.getText(), ROW_WINDOW_FONT));
        }
        double stateText = textWidth("Estado", HEADER_FONT);
        for (String state : STATE_TEXTS) {
            stateText = Math.max(stateText, textWidth(state, ROW_TEXT_FONT));
        }
        double stateWidth = stateText + extra;
        double windowWidth = windowText + extra;

        double codeFont = ROW_CODE_FONT;
        if (available > 0 && !longestCode.isEmpty()) {
            double codeRoom = available - stateWidth - windowWidth - MIN_SERVICE_WIDTH - extra;
            codeFont = fitSize(longestCode, codeRoom, ROW_CODE_FONT, ROW_CODE_MIN_FONT);
        }
        double codeWidth = Math.max(textWidth("Turno", HEADER_FONT),
                longestCode.isEmpty() ? 0 : textWidth(longestCode, codeFont)) + extra;

        setColumnWidths(listHeader, codeWidth, stateWidth, windowWidth);
        for (ActiveRow row : rowsById.values()) {
            setColumnWidths(row.root, codeWidth, stateWidth, windowWidth);
            setFontSize(row.code, codeFont);
        }
    }

    private void updateNote(DisplaySnapshot snapshot) {
        if (snapshot.waitingTruncated()) {
            listNoteLabel.setText("Se muestran los próximos " + snapshot.waitingTickets().size()
                    + " de " + snapshot.waitingTotal() + " turnos en espera.");
            listNoteLabel.setVisible(true);
            listNoteLabel.setManaged(true);
        } else {
            hideNote();
        }
    }

    private void hideNote() {
        listNoteLabel.setText("");
        listNoteLabel.setVisible(false);
        listNoteLabel.setManaged(false);
    }

    // Arma las páginas según el alto disponible. Si los llamados caben dejando espacio para la
    // espera, aparecen en todas las páginas y solo rota la espera; si no, rota la lista completa.
    // El reparto está en DisplayPaging; aquí solo se calcula cuántas filas caben.
    private List<List<ActiveRow>> buildPages() {
        int rowsPerPage = Math.max(1, (int) Math.floor((rowsBox.getHeight() + 0.5) / ROW_HEIGHT));
        return DisplayPaging.pages(calledRows, waitingRows, rowsPerPage, MIN_WAITING_SLOTS);
    }

    private void renderPage() {
        List<List<ActiveRow>> pages = buildPages();
        pageCount = pages.size();

        if (pages.isEmpty()) {
            pageIndex = 0;
            rowsBox.getChildren().setAll(emptyRowsLabel);
            pageLabel.setText("");
            return;
        }

        pageIndex = Math.min(pageIndex, pageCount - 1);
        List<ActiveRow> page = pages.get(pageIndex);
        List<Node> nodes = new ArrayList<>(page.size());
        for (int index = 0; index < page.size(); index++) {
            ActiveRow row = page.get(index);
            row.root.pseudoClassStateChanged(ODD, index % 2 == 1);
            nodes.add(row.root);
        }
        rowsBox.getChildren().setAll(nodes);
        pageLabel.setText(pageCount > 1 ? "Página " + (pageIndex + 1) + " de " + pageCount : "");
    }

    private void nextPage() {
        if (pageCount > 1) {
            pageIndex = (pageIndex + 1) % pageCount;
            renderPage();
        }
    }

    // Conserva los últimos datos visibles e informa desde cuándo dejaron de actualizarse.
    private void showFailure(Exception error) {
        System.err.println("No se pudo actualizar la pantalla pública. Detalle: " + error);
        String message = lastSuccessfulUpdate == null
                ? "Sin conexión con el servidor. Reintentando automáticamente."
                : "Sin conexión. Datos sin actualizar desde las "
                + CLOCK.format(lastSuccessfulUpdate) + ". Reintentando automáticamente.";
        connectionLabel.setText(message);
        connectionLabel.pseudoClassStateChanged(OFFLINE, true);
    }

    private static String stateName(TicketRow ticket) {
        return switch (ticket.state()) {
            case "EN_ESPERA" ->
                "En espera";
            case "LLAMADO" ->
                ticket.callCount() > 1 ? "Rellamado" : "Llamado";
            case "EN_ATENCION" ->
                "En atención";
            default ->
                ticket.state();
        };
    }

    // Ancho del texto en negrita con la fuente de la pantalla (el CSS usa la misma familia).
    private static double textWidth(String text, double size) {
        Text measure = new Text(text);
        measure.setFont(Font.font(FONT_FAMILY, FontWeight.BOLD, size));
        return measure.getLayoutBounds().getWidth();
    }

    // Mayor tamaño de letra (entre min y max) con el que el texto cabe en el ancho disponible.
    // Antes de la primera distribución el ancho es cero y se usa el máximo.
    private static double fitSize(String text, double available, double max, double min) {
        if (available <= 0 || text == null || text.isEmpty()) {
            return max;
        }
        double width = textWidth(text, max);
        if (width <= available) {
            return max;
        }
        double size = Math.max(min, Math.floor(max * available / width));
        while (size > min && textWidth(text, size) > available) {
            size--;
        }
        return size;
    }

    private static void setFontSize(Label label, double size) {
        label.setStyle("-fx-font-size: " + size + "px;");
    }

    // Columnas: Turno, Servicio, Estado, Ventanilla. Servicio es la única que crece y la única
    // que puede abreviarse; las demás reciben un ancho fijo calculado.
    private static void configureColumns(GridPane grid) {
        for (int index = 0; index < 4; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setHalignment(HPos.LEFT);
            column.setFillWidth(true);
            if (index == 1) {
                column.setHgrow(Priority.ALWAYS);
                column.setMinWidth(0);
            } else {
                column.setHgrow(Priority.NEVER);
            }
            grid.getColumnConstraints().add(column);
        }
    }

    private static void setColumnWidths(GridPane grid, double code, double state, double window) {
        setFixedWidth(grid.getColumnConstraints().get(0), code);
        setFixedWidth(grid.getColumnConstraints().get(2), state);
        setFixedWidth(grid.getColumnConstraints().get(3), window);
    }

    private static void setFixedWidth(ColumnConstraints column, double width) {
        column.setMinWidth(width);
        column.setPrefWidth(width);
        column.setMaxWidth(width);
    }

    private static Label addCell(GridPane grid, String text, int column, String styleClass,
            boolean canShrink) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(canShrink ? 0 : Region.USE_PREF_SIZE);
        label.setWrapText(false);
        grid.add(label, column, 0);
        return label;
    }

    // Fila visual de un ticket activo. Se crea una vez por identificador y se actualiza en
    // las capturas siguientes. El estado siempre se muestra como texto.
    private static final class ActiveRow {

        private final GridPane root = new GridPane();
        private final Label code;
        private final Label service;
        private final Label state;
        private final Label window;

        private ActiveRow() {
            root.getStyleClass().add("active-row");
            root.setMinHeight(ROW_HEIGHT);
            root.setPrefHeight(ROW_HEIGHT);
            root.setMaxHeight(ROW_HEIGHT);
            root.setMaxWidth(Double.MAX_VALUE);
            configureColumns(root);
            code = addCell(root, "", 0, "code-cell", false);
            service = addCell(root, "", 1, "service-cell", true);
            state = addCell(root, "", 2, "state-cell", false);
            window = addCell(root, "", 3, "window-cell", false);
        }

        private void update(TicketRow ticket, boolean latest) {
            code.setText(ticket.visualCode());
            service.setText(ticket.serviceName() + (ticket.priority() ? " · Preferencial" : ""));
            // «Ahora» acompaña solo a un llamado vigente; un turno en atención conserva su estado.
            state.setText(stateName(ticket)
                    + (latest && "LLAMADO".equals(ticket.state()) ? " · Ahora" : ""));
            window.setText(ticket.windowId() == null ? "—" : String.valueOf(ticket.windowId()));
            root.pseudoClassStateChanged(CALLED, "LLAMADO".equals(ticket.state()));
            root.pseudoClassStateChanged(ATTENTION, "EN_ATENCION".equals(ticket.state()));
            root.pseudoClassStateChanged(WAITING, "EN_ESPERA".equals(ticket.state()));
            root.pseudoClassStateChanged(LATEST, latest);
        }
    }

    public void close() {
        closed = true;
        recallHighlight.stop();
        if (clock != null) {
            clock.stop();
        }
        if (pager != null) {
            pager.stop();
        }
        if (refreshService != null) {
            refreshService.close();
        }
    }
}
