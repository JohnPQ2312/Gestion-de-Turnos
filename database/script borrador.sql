CREATE TABLE funcionario (
    id_funcionario INT UNSIGNED NOT NULL AUTO_INCREMENT,
    identificacion VARCHAR(30) NOT NULL,
    nombre_completo VARCHAR(120) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,

    PRIMARY KEY (id_funcionario),
    UNIQUE (identificacion)
) ENGINE = InnoDB;

CREATE TABLE servicio (
    id_servicio INT UNSIGNED NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(80) NOT NULL,
    prefijo VARCHAR(5) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,

    PRIMARY KEY (id_servicio),
    UNIQUE KEY uk_servicio_nombre (nombre),
    UNIQUE KEY uk_servicio_prefijo (prefijo)
) ENGINE = InnoDB;

CREATE TABLE ventanilla (
    id_ventanilla INT UNSIGNED NOT NULL AUTO_INCREMENT,
    numero INT UNSIGNED NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'INACTIVA',

    PRIMARY KEY (id_ventanilla),
    UNIQUE KEY uk_ventanilla_numero (numero)
) ENGINE = InnoDB;

CREATE TABLE usuario (
    id_usuario INT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_funcionario INT UNSIGNED NULL,
    nombre_usuario VARCHAR(50) NOT NULL,
    clave_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,

    PRIMARY KEY (id_usuario),
    UNIQUE KEY uk_usuario_nombre (nombre_usuario),
    UNIQUE KEY uk_usuario_funcionario (id_funcionario),

    CONSTRAINT fk_usuario_funcionario
        FOREIGN KEY (id_funcionario)
        REFERENCES funcionario (id_funcionario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE ventanilla_servicio (
    id_ventanilla INT UNSIGNED NOT NULL,
    id_servicio INT UNSIGNED NOT NULL,

    PRIMARY KEY (id_ventanilla, id_servicio),

    CONSTRAINT fk_ventanilla_servicio_ventanilla
        FOREIGN KEY (id_ventanilla)
        REFERENCES ventanilla (id_ventanilla)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    CONSTRAINT fk_ventanilla_servicio_servicio
        FOREIGN KEY (id_servicio)
        REFERENCES servicio (id_servicio)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE contador_turno (
    id_servicio INT UNSIGNED NOT NULL,
    ultimo_numero INT UNSIGNED NOT NULL DEFAULT 0,

    PRIMARY KEY (id_servicio),

    CONSTRAINT fk_contador_turno_servicio
        FOREIGN KEY (id_servicio)
        REFERENCES servicio (id_servicio)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE turno (
    id_turno BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_servicio INT UNSIGNED NOT NULL,
    id_ventanilla INT UNSIGNED NULL,
    id_funcionario INT UNSIGNED NULL,

    numero INT UNSIGNED NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'EN_ESPERA',

    fecha_hora_generacion DATETIME
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    fecha_hora_llamado DATETIME NULL,
    fecha_hora_inicio DATETIME NULL,
    fecha_hora_finalizacion DATETIME NULL,

    PRIMARY KEY (id_turno),

    UNIQUE KEY uk_turno_codigo (codigo),
    UNIQUE KEY uk_turno_numero_servicio (
        id_servicio,
        numero
    ),

    CONSTRAINT fk_turno_servicio
        FOREIGN KEY (id_servicio)
        REFERENCES servicio (id_servicio)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_turno_ventanilla
        FOREIGN KEY (id_ventanilla)
        REFERENCES ventanilla (id_ventanilla)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_turno_funcionario
        FOREIGN KEY (id_funcionario)
        REFERENCES funcionario (id_funcionario)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    INDEX idx_turno_asignacion (
        id_servicio,
        estado,
        fecha_hora_generacion,
        id_turno
    ),

    INDEX idx_turno_fecha_generacion (
        fecha_hora_generacion
    ),

    INDEX idx_turno_fecha_finalizacion (
        fecha_hora_finalizacion
    )
) ENGINE = InnoDB;