-- =====================================================================
-- Migración 002: estado PAUSADA de la ventanilla
-- =====================================================================
-- Aplicar UNA vez, después de la migración 001, sobre una base con la
-- estructura anterior. No modifica registros.
--
-- PAUSADA es una no disponibilidad temporal decidida por el operador:
-- la asignación sigue abierta y el turno en curso puede completarse, pero
-- no se llaman turnos nuevos. Es distinta de INACTIVA, que es la baja
-- administrativa de la ventanilla.
--
-- Requiere MySQL 8.0.19 o superior (ALTER TABLE ... DROP CHECK).
-- =====================================================================

ALTER TABLE `window` DROP CHECK ck_window_state;

ALTER TABLE `window`
    ADD CONSTRAINT ck_window_state
        CHECK (window_state IN ('ACTIVA', 'INACTIVA', 'PAUSADA'));
