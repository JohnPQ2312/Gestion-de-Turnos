package cr.ac.una.model;

/**
 * ACTIVA: disponible para llamar turnos.
 * PAUSADA: no disponible temporalmente por decisión del operador; conserva su
 * asignación y permite completar el turno en curso.
 * INACTIVA: baja administrativa.
 */
public enum WindowState {
    ACTIVA,
    INACTIVA,
    PAUSADA
}
