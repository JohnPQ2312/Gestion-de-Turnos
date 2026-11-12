-- Ejecutar una sola vez sobre la base existente, con CoreServer detenido.
-- Seleccionar primero la base correspondiente. No ejecutar el DDL completo.
-- Las fechas históricas mantienen su valor; se amplía su precisión.
ALTER TABLE ticket
    MODIFY called_at TIMESTAMP(6) NULL DEFAULT NULL,
    MODIFY started_at TIMESTAMP(6) NULL DEFAULT NULL,
    MODIFY ended_at TIMESTAMP(6) NULL DEFAULT NULL,
    ADD COLUMN last_called_at TIMESTAMP(6) NULL DEFAULT NULL AFTER called_at,
    ADD COLUMN call_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER last_called_at;

-- El único llamado conocido de los turnos anteriores es el primero.
UPDATE ticket
SET last_called_at = called_at, call_count = 1
WHERE called_at IS NOT NULL AND last_called_at IS NULL AND call_count = 0;

ALTER TABLE ticket
    ADD CONSTRAINT ck_ticket_call_history CHECK (
        (ticket_state = 'EN_ESPERA' AND last_called_at IS NULL AND call_count = 0)
        OR (ticket_state <> 'EN_ESPERA' AND last_called_at IS NOT NULL
            AND call_count >= 1 AND last_called_at >= called_at
            AND (started_at IS NULL OR started_at >= last_called_at))
    ),
    ADD INDEX idx_ticket_last_called (last_called_at, ticket_id);
