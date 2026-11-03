package cr.ac.una.QueueDisplayScreen.controller;

import cr.ac.una.QueueDisplayScreen.model.TicketRow;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class DisplayScreenController {

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
    private void initialize() {
        codeColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(data.getValue().visualCode()));
        serviceColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(data.getValue().serviceName()));
        attentionColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(
                        data.getValue().priority() ? "Preferencial" : "Regular"));
        stateColumn.setCellValueFactory(data
                -> new ReadOnlyStringWrapper(data.getValue().state().replace('_', ' ')));
    }

    public void showTickets(List<TicketRow> tickets) {
        Objects.requireNonNull(tickets, "La lista de turnos es obligatoria.");
        ticketsTable.getItems().setAll(tickets);
        ticketsTable.setPlaceholder(new Label("Todavía no hay turnos registrados."));
    }
}
