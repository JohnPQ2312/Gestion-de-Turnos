-- Migración 001: credenciales de acceso de los funcionarios
-- Aplicar una sola vez sobre una base que ya tiene la estructura de
-- "esquema.sql" anterior a esta migración. No borra ni modifica
-- registros existentes.
--
-- Modelo: la identidad, el rol y el estado siguen en `operator`
-- (operator_username, operator_role, operator_state). Esta tabla guarda solo
-- el hash de la contraseña. Un funcionario sin fila aquí no puede iniciar
-- sesión: no se asigna ninguna contraseña por defecto.
--
-- password_hash usa el formato
--   pbkdf2_sha256$<iteraciones>$<sal en Base64>$<hash en Base64>
-- generado por CoreServer (cr.ac.una.security.PasswordHasher).
--
-- Las primeras contraseñas se crean con cr.ac.una.config.CredentialSetup
-- (ver database/INICIALIZAR_ACCESO.md). Nunca se insertan desde un script.

CREATE TABLE operator_credential (
    operator_id INT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (operator_id),

    CONSTRAINT ck_operator_credential_format
        CHECK (password_hash LIKE 'pbkdf2_sha256$%'),

    CONSTRAINT fk_operator_credential_operator
        FOREIGN KEY (operator_id)
        REFERENCES `operator` (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
