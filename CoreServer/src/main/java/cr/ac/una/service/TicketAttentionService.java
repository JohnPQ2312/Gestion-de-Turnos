package cr.ac.una.service;

import cr.ac.una.model.OperatorWindowAssignment;
import cr.ac.una.model.ServiceWindow;
import cr.ac.una.model.Ticket;
import cr.ac.una.repository.OperatorRepository;
import cr.ac.una.repository.OperatorWindowAssignmentRepository;
import cr.ac.una.repository.ServiceWindowRepository;
import cr.ac.una.repository.TicketCallingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class TicketAttentionService {

    private final TicketCallingRepository ticketRepository;
    private final OperatorRepository operatorRepository;
    private final ServiceWindowRepository windowRepository;
    private final OperatorWindowAssignmentRepository assignmentRepository;

    public TicketAttentionService(
            TicketCallingRepository ticketRepository,
            OperatorRepository operatorRepository,
            ServiceWindowRepository windowRepository,
            OperatorWindowAssignmentRepository assignmentRepository
    ) {
        this.ticketRepository = Objects.requireNonNull(
                ticketRepository, "El repositorio de turnos es obligatorio."
        );
        this.operatorRepository = Objects.requireNonNull(
                operatorRepository, "El repositorio de operadores es obligatorio."
        );
        this.windowRepository = Objects.requireNonNull(
                windowRepository, "El repositorio de ventanillas es obligatorio."
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "El repositorio de asignaciones es obligatorio."
        );
    }

    public List<OperatorWindowAssignment> listActiveAssignments() {
        List<OperatorWindowAssignment> activeAssignments = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (OperatorWindowAssignment assignment : assignmentRepository.findAll()) {
            if (!assignment.isOpen() || assignment.getStartedAt().isAfter(now)) {
                continue;
            }

            if (!operatorRepository.isActive(assignment.getOperatorId())) {
                continue;
            }

            Optional<ServiceWindow> window = windowRepository.findById(
                    assignment.getWindowId()
            );

            // Una ventanilla en pausa sigue en la lista: su operador debe poder
            // ver su turno, completarlo y reanudar.
            if (window.isPresent() && window.get().isOperational()) {
                activeAssignments.add(assignment);
            }
        }

        return List.copyOf(activeAssignments);
    }

    public Optional<Ticket> callNext(long assignmentId) {
        if (assignmentId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador de la asignación debe ser positivo."
            );
        }

        OperatorWindowAssignment assignment = assignmentRepository
                .findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                "No existe la asignación " + assignmentId + "."
        ));

        if (!assignment.isOpen()
                || assignment.getStartedAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "La asignación debe estar abierta y haber iniciado."
            );
        }

        if (!operatorRepository.isActive(assignment.getOperatorId())) {
            throw new IllegalStateException(
                    "El operador no existe o está inactivo."
            );
        }

        ServiceWindow window = windowRepository
                .findById(assignment.getWindowId())
                .orElseThrow(() -> new IllegalStateException(
                "La ventanilla asignada no existe."
        ));

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

        Set<String> eligibleServiceCodes = new HashSet<>(
                operatorRepository.findServiceCodes(
                        assignment.getOperatorId()
                )
        );

        eligibleServiceCodes.retainAll(
                windowRepository.findServiceCodes(
                        assignment.getWindowId()
                )
        );

        if (eligibleServiceCodes.isEmpty()) {
            throw new IllegalStateException(
                    "El operador y la ventanilla no comparten "
                    + "servicios habilitados."
            );
        }

        return ticketRepository.callNext(
                assignment,
                Set.copyOf(eligibleServiceCodes),
                window.getAttentionMode()
        );
    }
}
