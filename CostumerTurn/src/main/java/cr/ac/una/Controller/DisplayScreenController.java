package cr.ac.una.Controller;

import cr.ac.una.model.Ticket;
import cr.ac.una.repository.InMemoryTicketRepository;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class DisplayScreenController {

    private InMemoryTicketRepository ticketRepository;

    @FXML
    private TableView<Ticket> ticketsTable;

    @FXML
    private TableColumn<Ticket, String> codeColumn;

    @FXML
    private TableColumn<Ticket, String> serviceColumn;

    @FXML
    private TableColumn<Ticket, String> attentionColumn;

    @FXML
    private TableColumn<Ticket, String> stateColumn;

    @FXML
    private void initialize() {
        codeColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getVisualCode()
                )
        );

        serviceColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getService().getName()
                )
        );

        attentionColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().isPriority()
                                ? "Preferencial" : "Regular"
                )
        );

        stateColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getState().name()
                                .replace('_', ' ')
                )
        );
    }

    public void setTicketRepository(
            InMemoryTicketRepository ticketRepository) {

        this.ticketRepository = ticketRepository;
        refreshTickets();
    }

    public void refreshTickets() {
        ticketsTable.getItems().setAll(
                ticketRepository.findRegularTickets()
        );

        ticketsTable.getItems().addAll(
                ticketRepository.findPriorityTickets()
        );
    }
}