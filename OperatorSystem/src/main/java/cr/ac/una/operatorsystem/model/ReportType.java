package cr.ac.una.operatorsystem.model;

/**
 * Las cinco consultas de la sección 13 del enunciado, con el comando del
 * servidor y el criterio que se muestra al usuario.
 */
public enum ReportType {

    ATTENDED(
            "REPORT_ATTENDED",
            "1. Turnos atendidos entre fechas",
            "Turnos finalizados cuya atención terminó entre las fechas indicadas (días completos)."
    ),
    BY_OPERATOR(
            "REPORT_BY_OPERATOR",
            "2. Personas atendidas por funcionario",
            "Turnos finalizados en el rango, atribuidos al funcionario de la asignación con que se atendió cada turno."
    ),
    BY_SERVICE(
            "REPORT_BY_SERVICE",
            "3. Turnos emitidos por servicio",
            "Turnos emitidos (generados) en el rango, en cualquier estado. No es la cantidad de finalizados."
    ),
    WAIT_TIMES(
            "REPORT_WAIT_TIMES",
            "4. Tiempo de espera",
            "Desde la generación hasta el inicio de la atención, para turnos cuya atención inició en el rango. Formato h:mm:ss."
    ),
    ATTENTION_TIMES(
            "REPORT_ATTENTION_TIMES",
            "5. Tiempo de atención",
            "Desde el inicio hasta el final de la atención, para turnos finalizados en el rango. Formato h:mm:ss."
    );

    private final String command;
    private final String title;
    private final String criterion;

    ReportType(String command, String title, String criterion) {
        this.command = command;
        this.title = title;
        this.criterion = criterion;
    }

    public String command() {
        return command;
    }

    public String title() {
        return title;
    }

    public String criterion() {
        return criterion;
    }

    @Override
    public String toString() {
        return title;
    }
}
