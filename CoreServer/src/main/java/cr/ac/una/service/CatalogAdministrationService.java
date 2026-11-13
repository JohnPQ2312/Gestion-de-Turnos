package cr.ac.una.service;

import cr.ac.una.model.AdministrationActor;
import cr.ac.una.model.ManagedService;
import cr.ac.una.model.ManagedWindow;
import cr.ac.una.repository.CatalogAdministrationRepository;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

// Valida el formato de servicios y ventanillas antes de entrar al repositorio.
// Los duplicados y las dependencias se comprueban dentro de la transacción que los modifica.
public class CatalogAdministrationService {

    // Código estable: empieza con letra; letras, dígitos o guion bajo; VARCHAR(20).
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,19}");
    private static final String PREFIX_RULE
            = "El prefijo debe tener de 1 a 5 letras mayúsculas sin tildes, sin dígitos ni espacios.";
    private static final int MAX_WINDOW_ID = 999;

    private final CatalogAdministrationRepository repository;

    public CatalogAdministrationService(CatalogAdministrationRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<ManagedService> services(AdministrationActor actor) {
        return repository.listServices(actor);
    }

    public void createService(AdministrationActor actor, String code, String name, String prefix) {
        String checked = storedPrefix(prefix);
        if (!ManagedService.isValidNewPrefix(checked)) throw new IllegalArgumentException(PREFIX_RULE);
        repository.createService(actor, code(code), name(name), checked);
    }

    // El formato estricto se aplica en el repositorio solo si el prefijo cambia,
    // para no bloquear la edición del nombre de un servicio anterior a esta regla.
    public void updateService(AdministrationActor actor, String code, String name, String prefix) {
        repository.updateService(actor, storedCode(code), name(name), storedPrefix(prefix));
    }

    public void setServiceActive(AdministrationActor actor, String code, boolean active) {
        repository.setServiceActive(actor, storedCode(code), active);
    }

    public List<ManagedWindow> windows(AdministrationActor actor) {
        return repository.listWindows(actor);
    }

    public void createWindow(AdministrationActor actor, int id, String mode) {
        repository.createWindow(actor, windowId(id), mode(mode));
    }

    public void updateWindowMode(AdministrationActor actor, int id, String mode) {
        repository.updateWindowMode(actor, windowId(id), mode(mode));
    }

    public String setWindowActive(AdministrationActor actor, int id, boolean active) {
        return repository.setWindowActive(actor, windowId(id), active);
    }

    // Código de un servicio nuevo.
    private static String code(String value) {
        String result = value == null ? "" : value.trim();
        if (!CODE.matcher(result).matches()) {
            throw new IllegalArgumentException("El código del servicio es obligatorio: de 1 a 20 caracteres, "
                    + "inicia con letra mayúscula y solo usa letras mayúsculas, dígitos o guion bajo.");
        }
        return result;
    }

    // Código de un servicio existente: identifica la fila; no se reinterpreta su formato.
    private static String storedCode(String value) {
        if (value == null || value.isBlank() || value.length() > 20 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Seleccione un servicio válido.");
        }
        return value;
    }

    private static String name(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("El nombre del servicio es obligatorio.");
        String result = value.trim();
        if (result.codePointCount(0, result.length()) > 80 || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("El nombre del servicio admite hasta 80 caracteres y no permite caracteres de control.");
        }
        return result;
    }

    private static String storedPrefix(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 5 || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(PREFIX_RULE);
        }
        return result;
    }

    private static int windowId(int id) {
        if (id <= 0 || id > MAX_WINDOW_ID) {
            throw new IllegalArgumentException("El número de ventanilla debe ser un entero entre 1 y " + MAX_WINDOW_ID + ".");
        }
        return id;
    }

    private static String mode(String value) {
        if (!"MIXED".equals(value) && !"PRIORITY_ONLY".equals(value)) {
            throw new IllegalArgumentException("Seleccione una modalidad válida: mixta o solo preferencial.");
        }
        return value;
    }
}
