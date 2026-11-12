-- =====================================================================
-- Gestión de Turnos - Datos iniciales para pruebas
-- =====================================================================
-- Uso:
--   1. Crear una base VACÍA y seleccionarla (ver las dos líneas de abajo).
--   2. Ejecutar primero "script borrador.sql" (estructura de las tablas).
--   3. Ejecutar este archivo.
--
-- IMPORTANTE: no ejecutar sobre la base compartida del equipo que ya tiene
-- datos (gamabasis_p3g9). Los identificadores fijos chocarían con los
-- registros existentes. Este script es para instalaciones nuevas.
--
-- Los datos son ficticios. Los prefijos tienen una sola letra (A, B, C) para
-- que ningún código visual pueda repetirse entre servicios.
--
-- CREATE DATABASE gestion_turnos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- USE gestion_turnos;
-- =====================================================================

-- Asegura que las tildes se guarden bien aunque el cliente use otra
-- codificación por defecto (por ejemplo, latin1 en la consola).
SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- Funcionarios
-- ---------------------------------------------------------------------
INSERT INTO `operator` (operator_id, operator_name, operator_username, operator_role, operator_state) VALUES
    (1, 'Ana Mora Solís',      'amora',   'OPERADOR', 'ACTIVO'),
    (2, 'Luis Vargas Rojas',   'lvargas', 'OPERADOR', 'ACTIVO'),
    (3, 'María Jiménez Castro','mjimenez','OPERADOR', 'ACTIVO'),
    (4, 'Carlos Araya Brenes', 'caraya',  'OPERADOR', 'INACTIVO'),
    (5, 'Administración',      'admin',   'ADMIN',    'ACTIVO');

-- Ningún funcionario recibe contraseña desde este script. Para iniciar
-- sesión, crear cada credencial con cr.ac.una.config.CredentialSetup
-- (ver database/INICIALIZAR_ACCESO.md).

-- ---------------------------------------------------------------------
-- Servicios y su consecutivo (cada servicio necesita su fila en
-- ticket_sequence; sin ella no se pueden generar turnos)
-- ---------------------------------------------------------------------
INSERT INTO service (service_code, services_name, service_prefix, service_state) VALUES
    ('INFO', 'Información general', 'A', 'ACTIVO'),
    ('TRAM', 'Trámites',            'B', 'ACTIVO'),
    ('PAGO', 'Pagos',               'C', 'ACTIVO');

INSERT INTO ticket_sequence (service_id, last_number) VALUES
    ('INFO', 0),
    ('TRAM', 0),
    ('PAGO', 0);

-- ---------------------------------------------------------------------
-- Ventanillas
--   1 y 2: mixtas (regulares y preferenciales)
--   3: solo preferenciales (característica de innovación)
--   4: inactiva, para probar el rechazo de ventanillas inactivas
-- ---------------------------------------------------------------------
INSERT INTO `window` (window_id, window_state, attention_mode) VALUES
    (1, 'ACTIVA',   'MIXED'),
    (2, 'ACTIVA',   'MIXED'),
    (3, 'ACTIVA',   'PRIORITY_ONLY'),
    (4, 'INACTIVA', 'MIXED');

-- Servicios habilitados en cada ventanilla
INSERT INTO window_service (window_id, service_code) VALUES
    (1, 'INFO'), (1, 'TRAM'),
    (2, 'INFO'), (2, 'TRAM'), (2, 'PAGO'),
    (3, 'INFO'), (3, 'TRAM'), (3, 'PAGO'),
    (4, 'PAGO');

-- Servicios que puede atender cada funcionario
INSERT INTO operator_service (operator_id, service_code) VALUES
    (1, 'INFO'), (1, 'TRAM'),
    (2, 'INFO'), (2, 'TRAM'), (2, 'PAGO'),
    (3, 'INFO'), (3, 'PAGO'),
    (4, 'PAGO');

-- ---------------------------------------------------------------------
-- Asignaciones abiertas (operador en ventanilla)
-- Se inician un minuto antes para evitar diferencias de reloj entre
-- la computadora y el servidor MySQL.
-- Operador 1 -> ventanilla 1 y operador 2 -> ventanilla 2 son las que
-- usa pruebas/ConcurrentCallingCheck.java.
-- ---------------------------------------------------------------------
INSERT INTO operator_window_assignment (assignment_id, operator_id, window_id, started_at) VALUES
    (1, 1, 1, CURRENT_TIMESTAMP - INTERVAL 1 MINUTE),
    (2, 2, 2, CURRENT_TIMESTAMP - INTERVAL 1 MINUTE),
    (3, 3, 3, CURRENT_TIMESTAMP - INTERVAL 1 MINUTE);

COMMIT;

-- ---------------------------------------------------------------------
-- Comprobaciones rápidas después de ejecutar
-- ---------------------------------------------------------------------
-- SELECT * FROM service;                     -- 3 filas
-- SELECT * FROM ticket_sequence;             -- 3 filas en 0
-- SELECT * FROM operator_window_assignment;  -- 3 filas con ended_at NULL
--
-- Servicios que puede llamar cada asignación (intersección operador/ventanilla):
-- SELECT a.assignment_id, a.operator_id, a.window_id, os.service_code
-- FROM operator_window_assignment a
-- JOIN operator_service os ON os.operator_id = a.operator_id
-- JOIN window_service ws ON ws.window_id = a.window_id
--                       AND ws.service_code = os.service_code
-- WHERE a.ended_at IS NULL
-- ORDER BY a.assignment_id, os.service_code;
