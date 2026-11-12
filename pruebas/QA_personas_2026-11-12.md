# Validación de administración de personas — 12 de noviembre de 2026

Base de implementación: `e8dcca8`, más los cambios del bloque de usuarios, funcionarios y credenciales. No se modificó la base compartida para estas pruebas.

## Resultado

Cuatro proyectos compilados. **22 escenarios de servidor y 10 de interfaz aprobados, sin fallos**, sobre MySQL 8.0.32 local desechable y el servidor TCP del proyecto. Se repitieron además **24 escenarios de servidor y 10 de interfaz del rellamado**, todos aprobados.

Son 32 escenarios del bloque nuevo y 34 de regresión. Las repeticiones internas no se suman. Se usó JDK 25.0.3, objetivo de compilación Java 21 y JavaFX 13; no se probó por separado un runtime JDK 21.

La configuración de prueba apunta exclusivamente a `127.0.0.1:33317`, catálogos `turnos_qa` y `turnos_migracion_qa`. Los auxiliares comprueban host/catálogo antes de escribir. El servidor administrativo temporal utiliza el puerto 33319; la regresión, 33318. No se utilizan las credenciales de la base compartida.

## Cobertura

- Listados administrativos sin hashes ni contraseñas; indicadores de asignación y credencial.
- Alta de funcionario y contraseña con login real; rollback de ambos registros al provocar un error SQL en la credencial.
- Formatos, longitudes, perfil no habilitado, contraseña corta, duplicados y parámetros de protocolo inválidos.
- Edición, baja y reactivación con identidad conservada; rechazo de cambio de perfil/estado mientras exista una asignación abierta.
- Protección del último ADMIN activo con credencial, incluidas dos degradaciones simultáneas.
- Cambio de contraseña, revocación del acceso anterior y conservación de sesiones con una credencial posterior, incluso si una revocación anterior se ejecuta con retraso.
- Revalidación administrativa sin esperar el plazo de caché y rechazo de una identidad capturada antes de cambiar su contraseña.
- Usuarios de turnos: alta, edición, baja, reactivación, duplicados simultáneos y rechazo de baja con ticket pendiente, conservando su relación.
- Rechazo de todos los comandos nuevos sin sesión o desde OPERADOR; cliente TCP real y textos con acentos/separadores.
- Baja de un funcionario concurrente con llamado: no queda un turno atendido por un funcionario desactivado.
- FXML/CSS reales: tamaños mínimos, formularios, cancelación sin escritura, alta, edición, baja/reactivación, contraseña enmascarada y limpieza del campo.
- Mensaje de rechazo, regreso al acceso después de editar la cuenta propia y separación de pestañas.
- Confirmación perdida después de guardar: una sola escritura, aviso de incertidumbre, edición bloqueada hasta actualizar y recuperación de lectura.
- Botón Personas no visible por defecto y habilitación ADMIN; cabecera adaptable sin botones fuera del ancho mínimo probado.
- Regresión del llamado, pausa, rellamado, ciclo de atención, snapshots V1/V2 y pantalla pública.

## Evidencias y límites

- [Servidor: 22 escenarios](evidencias/personas_2026-11-12/servidor.log).
- [Interfaz: 10 escenarios](evidencias/personas_2026-11-12/interfaz.log).
- [Regresión de servidor: 24 escenarios](evidencias/personas_2026-11-12/regresion-servidor.log).
- [Regresión de interfaz: 10 escenarios](evidencias/personas_2026-11-12/regresion-interfaz.log).

Las capturas se inspeccionaron en el tamaño mínimo de la vista y de la pantalla de atención. Los auxiliares y las capturas permanecen fuera del repositorio, en el espacio local de QA. Esta batería no está integrada en Maven/CI.

No se aplicó una migración porque el bloque reutiliza el esquema actual. No se creó un administrador en la base compartida ni se ejecutó allí la nueva administración. Quedan para los siguientes bloques las aperturas/cierres de asignaciones desde la interfaz, los cambios de servicios/ventanillas de Joshua, su concurrencia conjunta, las pruebas entre equipos y la medición de rendimiento remoto.

Las escrituras de personas se serializan mediante el bloqueo común de las cuentas ADMIN. Es una decisión conservadora para este alcance; no se presenta como una medición de escalabilidad. Las ediciones no incorporan un versionado optimista de formularios: varios administradores pueden sobrescribir datos editables en orden de guardado, manteniendo las reglas de acceso y asignación comprobadas en cada transacción.

La [guía de uso y contratos](../database/ADMINISTRAR_PERSONAS.md) explica cómo abrir la vista y qué condiciones se rechazan.
