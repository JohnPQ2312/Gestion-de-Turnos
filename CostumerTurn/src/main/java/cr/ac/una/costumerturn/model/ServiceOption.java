package cr.ac.una.costumerturn.model;

// Datos del servicio que la terminal muestra para su selección.
public record ServiceOption(String code, String name) {
    @Override
    public String toString() {
        return name;
    }
}
