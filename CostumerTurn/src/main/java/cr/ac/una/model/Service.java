package cr.ac.una.model;

public class Service {

    private final String code;
    private final String name;
    private final String prefix;

    public Service(String code, String name, String prefix) {
        this.code = code;
        this.name = name;
        this.prefix = prefix;
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