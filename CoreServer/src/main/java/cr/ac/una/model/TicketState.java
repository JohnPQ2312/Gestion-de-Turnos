package cr.ac.una.model;

// Define el recorrido permitido del turno desde la espera hasta la atención finalizada.
public enum TicketState {
    EN_ESPERA,
    LLAMADO,
    EN_ATENCION,
    FINALIZADO
}