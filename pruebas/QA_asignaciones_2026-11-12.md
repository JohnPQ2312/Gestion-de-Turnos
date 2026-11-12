# Validación de habilitaciones y asignaciones — 12 de noviembre de 2026

Base de implementación: `b3f1560`, más los cambios de este bloque. Cuatro proyectos compilados con objetivo Java 21; ejecución sobre JDK 25.0.3, JavaFX 13 y MySQL 8.0.32 local desechable.

## Resultados

- **29 escenarios de servidor aprobados**, sin fallos.
- **11 escenarios de interfaz aprobados**, sin fallos; capturas inspeccionadas a tamaño mínimo.
- Regresión de Personas: **22 de servidor y 10 de interfaz aprobados**.
- Regresión de rellamado: **24 de servidor y 10 de interfaz aprobados**.

Son 40 escenarios del bloque nuevo y 66 de regresión. Las rondas concurrentes internas y las ejecuciones repetidas después de ajustes no se suman como escenarios nuevos.

Se usaron las bases locales turnos_qa y turnos_migracion_qa en 127.0.0.1:33317. Los auxiliares comprueban host y catálogo antes de escribir. El servidor de configuración temporal utilizó el puerto 33320. No se usaron credenciales de la base compartida ni se modificaron sus tablas o registros.

## Cobertura relevante

- Captura coherente de los catálogos, enlaces e historial, sin hashes ni contraseñas.
- Todos los comandos nuevos denegados sin sesión o desde OPERADOR; parámetros, estados y destinatarios inválidos rechazados.
- Habilitación/retirada de funcionario y ventanilla, sin duplicar vínculos.
- Rechazo de servicios inactivos, retiradas con cola, servicio de un turno activo y pérdida de la última compatibilidad de una asignación abierta.
- Seis rondas de retiradas cruzadas simultáneas entre las habilitaciones de funcionario y ventanilla: se conserva una intersección útil.
- Apertura con servicio compatible; recuperación de la nueva asignación desde la sesión real del funcionario.
- Rechazo de apertura sin compatibilidad, con cuenta inactiva/sin credencial/perfil no habilitado o ventanilla inactiva/pausada.
- Dos aperturas simultáneas para un funcionario y dos funcionarios para una ventanilla: una sola ocupación.
- Cierre y reapertura con filas históricas distintas; rechazo de doble cierre y de cierre con LLAMADO/EN_ATENCION.
- Reanudación obligatoria antes de cerrar una ventanilla pausada, evitando dejarla sin una asignación utilizable para reanudar.
- Atribución histórica del reporte conservada después de reutilizar la ventanilla con otro funcionario.
- Seis rondas de cierre frente a llamado y seis de retirada frente a llamado: sin activos en asignaciones cerradas ni servicios retirados de una atención activa.
- Apertura frente a baja de funcionario: se mantiene la consistencia entre cuenta activa y ocupación abierta.
- Error SQL provocado al abrir: rollback y liberación del bloqueo; la apertura siguiente puede ejecutarse.
- Contención del bloqueo nombrado: espera limitada, rechazo sin escritura y recuperación posterior.
- Identidad administrativa capturada antes de un cambio de contraseña rechazada.
- Cliente TCP real, todas las operaciones y confirmación perdida después de guardar sin reenvío ni duplicación.
- Vistas JavaFX reales: selección explícita, cancelación, habilitaciones, apertura, rechazo de duplicado, historial, cierre, errores, controles de ADMIN y cabecera adaptable.
- Confirmación perdida en interfaz: una sola apertura, aviso completo, edición bloqueada hasta actualizar y recuperación del historial.
- Pérdida del permiso administrativo notificada para volver al acceso.

## Evidencias

- [Servidor: 29 escenarios](evidencias/asignaciones_2026-11-12/servidor.log).
- [Interfaz: 11 escenarios](evidencias/asignaciones_2026-11-12/interfaz.log).
- [Regresión de Personas, servidor](evidencias/asignaciones_2026-11-12/regresion-personas-servidor.log).
- [Regresión de Personas, interfaz](evidencias/asignaciones_2026-11-12/regresion-personas-interfaz.log).
- [Regresión de rellamado, servidor](evidencias/asignaciones_2026-11-12/regresion-rellamado-servidor.log).
- [Regresión de rellamado, interfaz](evidencias/asignaciones_2026-11-12/regresion-rellamado-interfaz.log).

Los auxiliares y las capturas permanecen en el espacio local de QA. No se incorporó una batería Maven/CI automática. No se certifican carga prolongada, un runtime JDK 21 separado ni despliegue entre computadoras.

El bloque reutiliza el esquema; no añadió migraciones. Falta contrastar e integrar las modificaciones administrativas de servicios y ventanillas cuando Joshua las entregue. Esas implementaciones deberán coordinar los bloqueos y las comprobaciones de compatibilidad. La regresión actual no certifica código todavía no integrado.

Las habilitaciones no incluyen historial individual de cada alta/baja de enlace: se conserva el historial de asignaciones y atenciones exigido por el modelo actual. El listado administrativo muestra todas las asignaciones; no se añadió paginación en este bloque.

Consultar [la guía de uso, contratos y concurrencia](../database/CONFIGURAR_ATENCION.md).
