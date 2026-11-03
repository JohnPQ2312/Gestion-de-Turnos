package cr.ac.una.operatorsystem.model;

// Datos del llamado que debe presentar la pantalla del operador.
public record CalledTicket(String visualCode, String serviceName, int operatorId, int windowId) {
}
