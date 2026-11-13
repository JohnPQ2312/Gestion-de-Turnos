package cr.ac.una.service;

import cr.ac.una.model.AttendedTicket;
import cr.ac.una.model.DurationReport;
import cr.ac.una.model.OperatorAttentionCount;
import cr.ac.una.model.ReportPeriod;
import cr.ac.una.model.ServiceTicketCount;
import cr.ac.una.repository.ReportRepository;
import java.util.List;
import java.util.Objects;

// Coordina las cinco consultas del historial y calcula los resúmenes de duración.
// Comprueba que el período exista antes de consultar el repositorio.
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = Objects.requireNonNull(
                reportRepository, "El repositorio de reportes es obligatorio."
        );
    }

    public List<AttendedTicket> attendedTickets(ReportPeriod period) {
        return reportRepository.findAttended(requirePeriod(period));
    }

    public List<OperatorAttentionCount> attentionByOperator(ReportPeriod period) {
        return reportRepository.countAttendedByOperator(requirePeriod(period));
    }

    public List<ServiceTicketCount> ticketsByService(ReportPeriod period) {
        return reportRepository.countByService(requirePeriod(period));
    }

    public DurationReport waitTimes(ReportPeriod period) {
        return DurationReport.of(reportRepository.findWaitTimes(requirePeriod(period)));
    }

    public DurationReport attentionTimes(ReportPeriod period) {
        return DurationReport.of(reportRepository.findAttentionTimes(requirePeriod(period)));
    }

    private ReportPeriod requirePeriod(ReportPeriod period) {
        return Objects.requireNonNull(period, "El período es obligatorio.");
    }
}
