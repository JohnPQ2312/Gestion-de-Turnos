CREATE TABLE `operator` (
    operator_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    operator_name VARCHAR(100) NOT NULL,
    operator_username VARCHAR(50) NOT NULL,
    operator_role VARCHAR(30) NOT NULL DEFAULT 'OPERADOR',
    operator_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    PRIMARY KEY (operator_id),
    UNIQUE KEY uk_operator_username (operator_username),
    CONSTRAINT ck_operator_role
        CHECK (operator_role IN ('ADMIN', 'OPERADOR', 'SUPERVISOR')),
    CONSTRAINT ck_operator_state
        CHECK (operator_state IN ('ACTIVO', 'INACTIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service (
    service_code VARCHAR(20) NOT NULL,
    services_name VARCHAR(80) NOT NULL,
    service_prefix VARCHAR(5) NOT NULL,
    service_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    op_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (service_code),
    UNIQUE KEY uk_service_prefix (service_prefix),
    CONSTRAINT ck_service_state
        CHECK (service_state IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_service_prefix_nonempty
        CHECK (CHAR_LENGTH(TRIM(service_prefix)) > 0),
    CONSTRAINT fk_service_operator
        FOREIGN KEY (op_id) REFERENCES `operator` (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `window` (
    window_id INT UNSIGNED NOT NULL,
    window_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    service_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (window_id),
    CONSTRAINT ck_window_state
        CHECK (window_state IN ('ACTIVA', 'INACTIVA')),
    CONSTRAINT fk_window_service
        FOREIGN KEY (service_id) REFERENCES service (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    user_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    user_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    PRIMARY KEY (user_id),
    CONSTRAINT ck_user_state
        CHECK (user_state IN ('ACTIVO', 'INACTIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_sequence (
    service_id VARCHAR(20) NOT NULL,
    last_number INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (service_id),
    CONSTRAINT fk_ticket_sequence_service
        FOREIGN KEY (service_id) REFERENCES service (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket (
    ticket_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_id VARCHAR(20) NOT NULL,
    window_id INT UNSIGNED NULL,
    user_id INT UNSIGNED NULL,
    operator_id INT UNSIGNED NULL,
    sequential_number INT UNSIGNED NOT NULL,
    visual_code VARCHAR(20) NOT NULL,
    ticket_state VARCHAR(20) NOT NULL DEFAULT 'EN_ESPERA',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP NULL DEFAULT NULL,
    started_at TIMESTAMP NULL DEFAULT NULL,
    ended_at TIMESTAMP NULL DEFAULT NULL,
    active_window_id INT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN ticket_state IN ('LLAMADO', 'EN_ATENCION')
             THEN window_id ELSE NULL END
    ) STORED,

    PRIMARY KEY (ticket_id),
    UNIQUE KEY uk_ticket_service_number (service_id, sequential_number),
    UNIQUE KEY uk_ticket_visual_code (visual_code),
    UNIQUE KEY uk_ticket_active_window (active_window_id),

    CONSTRAINT ck_ticket_number CHECK (sequential_number > 0),
    CONSTRAINT ck_ticket_state CHECK (
        ticket_state IN ('EN_ESPERA', 'LLAMADO', 'EN_ATENCION', 'FINALIZADO')
    ),
    CONSTRAINT ck_ticket_assignment CHECK (
        ticket_state = 'EN_ESPERA'
        OR (window_id IS NOT NULL AND operator_id IS NOT NULL)
    ),
    CONSTRAINT ck_ticket_state_times CHECK (
        (ticket_state = 'EN_ESPERA'
            AND called_at IS NULL AND started_at IS NULL AND ended_at IS NULL)
        OR (ticket_state = 'LLAMADO'
            AND called_at IS NOT NULL AND started_at IS NULL AND ended_at IS NULL)
        OR (ticket_state = 'EN_ATENCION'
            AND called_at IS NOT NULL AND started_at IS NOT NULL AND ended_at IS NULL)
        OR (ticket_state = 'FINALIZADO'
            AND called_at IS NOT NULL AND started_at IS NOT NULL AND ended_at IS NOT NULL)
    ),
    CONSTRAINT ck_ticket_time_order CHECK (
        (called_at IS NULL OR called_at >= created_at)
        AND (started_at IS NULL OR started_at >= called_at)
        AND (ended_at IS NULL OR ended_at >= started_at)
    ),
    CONSTRAINT fk_ticket_service
        FOREIGN KEY (service_id) REFERENCES service (service_code),
    CONSTRAINT fk_ticket_window
        FOREIGN KEY (window_id) REFERENCES `window` (window_id),
    CONSTRAINT fk_ticket_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_ticket_operator
        FOREIGN KEY (operator_id) REFERENCES `operator` (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_ticket_assignment
    ON ticket (service_id, ticket_state, created_at, ticket_id);
CREATE INDEX idx_ticket_created_at ON ticket (created_at);
