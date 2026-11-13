package cr.ac.una.repository;

import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.model.TicketDuration;
import java.util.List;

// Define las cinco consultas de solo lectura sobre el historial de turnos.
public interface ReportRepository {

    // Reporte de atenciones: turnos FINALIZADO cuya atención terminó en el período.
    List<AttendedTicket> findAttended(ReportPeriod period);

    // Reporte por funcionario: turnos FINALIZADO en el período, por funcionario (incluye ceros).
    List<OperatorAttentionCount> countAttendedByOperator(ReportPeriod period);

    // Reporte por servicio: turnos emitidos en el período, de todos los estados, por servicio (incluye
    // ceros).
    List<ServiceTicketCount> countByService(ReportPeriod period);

    // Reporte de espera: espera desde la generación hasta el inicio, para turnos iniciados en el
    // período.
    List<TicketDuration> findWaitTimes(ReportPeriod period);

    // Reporte de duración de atención: atención desde el inicio hasta el final, para turnos finalizados en el
    // período.
    List<TicketDuration> findAttentionTimes(ReportPeriod period);
}
