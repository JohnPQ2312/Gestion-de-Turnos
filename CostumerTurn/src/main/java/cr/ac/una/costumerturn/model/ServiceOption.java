package cr.ac.una.costumerturn.model;

// Conserva el código enviado al servidor y el nombre mostrado en la selección de servicios.
public record ServiceOption(String code, String name) {
    @Override
    public String toString() {
        return name;
    }
}
