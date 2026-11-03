package cr.ac.una.model;

public class Service {

    private final String code;
    private final String name;
    private final String prefix;

    public Service(String code, String name, String prefix) {
        this.code = requireText(code, "Código del servicio", 20);
        this.name = requireText(name, "Nombre del servicio", 80);
        this.prefix = requireText(prefix, "Prefijo del servicio", 5);
    }

    private static String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " es obligatorio."
            );
        }

        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " no puede superar "
                    + maxLength + " caracteres."
            );
        }

        return value;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public String toString() {
        return name;
    }
}
