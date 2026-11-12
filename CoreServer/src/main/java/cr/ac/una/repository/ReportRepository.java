package cr.ac.una.repository;

import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.model.TicketDuration;
import java.util.List;

/** Consultas de solo lectura sobre el historial de turnos (sección 13). */
public interface ReportRepository {

    /** Consulta 1: turnos FINALIZADO cuya atención terminó en el período. */
    List<AttendedTicket> findAttended(ReportPeriod period);

    /** Consulta 2: turnos FINALIZADO en el período, por funcionario (incluye ceros). */
    List<OperatorAttentionCount> countAttendedByOperator(ReportPeriod period);

    /** Consulta 3: turnos emitidos en el período, de todos los estados, por servicio (incluye ceros). */
    List<ServiceTicketCount> countByService(ReportPeriod period);

    /** Consulta 4: espera desde la generación hasta el inicio, para turnos iniciados en el período. */
    List<TicketDuration> findWaitTimes(ReportPeriod period);

    /** Consulta 5: atención desde el inicio hasta el final, para turnos finalizados en el período. */
    List<TicketDuration> findAttentionTimes(ReportPeriod period);
}
