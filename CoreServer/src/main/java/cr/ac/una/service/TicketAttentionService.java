package cr.ac.una.service;

import cr.ac.una.model.AssignmentCallContext;
import cr.ac.una.model.CalledTicket;
import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.ServiceWindow;
import cr.ac.una.repository.AssignmentCallContextRepository;
import cr.ac.una.repository.OperatorWindowAssignmentRepository;
import cr.ac.una.repository.TicketCallingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Comprueba la asignación y obtiene los servicios que comparten funcionario y ventanilla.
// El repositorio decide el siguiente turno compatible y confirma su llamado.
public class TicketAttentionService {

    private final TicketCallingRepository ticketRepository;
    private final OperatorWindowAssignmentRepository assignmentRepository;
    private final AssignmentCallContextRepository callContextRepository;

    public TicketAttentionService(
            TicketCallingRepository ticketRepository,
            OperatorWindowAssignmentRepository assignmentRepository,
            AssignmentCallContextRepository callContextRepository
    ) {
        this.ticketRepository = Objects.requireNonNull(
                ticketRepository, "El repositorio de turnos es obligatorio."
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "El repositorio de asignaciones es obligatorio."
        );
        this.callContextRepository = Objects.requireNonNull(
                callContextRepository,
                "El repositorio de datos previos al llamado es obligatorio."
        );
    }

    // Asignaciones que el funcionario indicado puede usar ahora: abiertas e iniciadas, con el
    // funcionario activo y la ventanilla operativa. Una ventanilla en pausa sigue en la lista: su
    // operador debe poder ver su turno, completarlo y reanudar. El filtro lo aplica una única consulta
    // del repositorio; no se leen las asignaciones de otros funcionarios.
    public List<OperatorWindowAssignment> listActiveAssignments(int operatorId) {
        if (operatorId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del funcionario debe ser positivo."
            );
        }

        return assignmentRepository.findUsableByOperator(operatorId, LocalDateTime.now());
    }

    // Cruza las habilitaciones del funcionario y la ventanilla antes de solicitar un turno
    // compatible. Las comprobaciones previas se leen en una sola conexión y se aplican en el mismo
    // orden y con los mismos mensajes que antes; la transacción del repositorio de llamado las repite
    // con bloqueos y decide el turno.
    public Optional<CalledTicket> callNext(long assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        AssignmentCallContext context = callContextRepository
                .findCallContext(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                "No existe la asignación " + assignmentId + "."
        ));
        OperatorWindowAssignment assignment = context.assignment();

        if (!assignment.isOpen()
                || assignment.getStartedAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "La asignación debe estar abierta y haber iniciado."
            );
        }

        if (!context.operatorActive()) {
            throw new IllegalStateException(
                    "El operador no existe o está inactivo."
            );
        }

        ServiceWindow window = context.window();
        if (window == null) {
            throw new IllegalStateException(
                    "La ventanilla asignada no existe."
            );
        }

        if (window.isPaused()) {
            throw new IllegalStateException(
                    "La ventanilla está en pausa. Reanúdela para llamar turnos."
            );
        }

        if (!window.isActive()) {
            throw new IllegalStateException(
                    "La ventanilla asignada está inactiva."
            );
        }

        Set<String> eligibleServiceCodes = context.sharedServiceCodes();
        if (eligibleServiceCodes.isEmpty()) {
            throw new IllegalStateException(
                    "El operador y la ventanilla no comparten "
                    + "servicios habilitados."
            );
        }

        return ticketRepository.callNext(
                assignment,
                eligibleServiceCodes,
                window.getAttentionMode()
        );
    }
}
