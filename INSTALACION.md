# Instalación con la base asignada por el docente

El docente asignó a cada grupo una base de datos MySQL; el proyecto utiliza la asignada al grupo (`gamabasis_p3g9`). Esa base ya tiene la estructura y datos de uso, así que esta guía solo conecta los cuatro proyectos a ella. Para crear una base propia desde cero, seguir [INSTALACION_BASE_PROPIA.md](INSTALACION_BASE_PROPIA.md).

## 1. Preparar las herramientas

- JDK 21 o superior, seleccionado también en NetBeans.
- NetBeans con soporte Java/Maven. Abrir las carpetas `CoreServer`, `CostumerTurn`, `OperatorSystem` y `QueueDisplayScreen` como proyectos separados.
- Acceso de red al servidor MySQL asignado y las credenciales que el docente entregó al grupo (servidor, puerto, usuario y contraseña). No hace falta instalar MySQL.
- Acceso a Internet para que Maven descargue las dependencias que todavía no estén en su caché. Los clientes usan JavaFX 13 mediante Maven.

## 2. No ejecutar los scripts de instalación

La base asignada ya contiene sus tablas y datos. No ejecutar `database/esquema.sql`, `database/datos_prueba.sql` ni las migraciones de `database/migraciones`: los identificadores fijos chocarían con los registros existentes.

Para comprobar que la estructura es la esperada, ejecutar estas consultas en phpMyAdmin con la base seleccionada:

```sql
SELECT COUNT(*) AS tablas
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';   -- 10

SELECT CONSTRAINT_TYPE, COUNT(*) AS cantidad
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE() AND CONSTRAINT_TYPE IN ('FOREIGN KEY', 'CHECK')
GROUP BY CONSTRAINT_TYPE;                                        -- FOREIGN KEY 11, CHECK 16
```

Dos exportaciones de esa base, hechas en momentos distintos, tienen la misma estructura y coinciden con `esquema.sql` en las diez tablas, sus columnas, tipos, valores por defecto, índices y las 11 claves foráneas. Las exportaciones no muestran las restricciones CHECK, y por eso la segunda consulta las cuenta. El servidor MySQL de la base es la versión 8.4.7.

## 3. Configurar MySQL en CoreServer

Copiar `CoreServer/src/main/resources/db.properties.example` como `db.properties` en esa misma carpeta (en NetBeans, **Other Sources** › `src/main/resources`) y completar sus valores, o crear el archivo UTF-8 con este contenido:

```properties
db.url=jdbc:mysql://SERVIDOR:PUERTO/gamabasis_p3g9?connectionTimeZone=SERVER&preserveInstants=true
db.usuario=USUARIO_MYSQL
db.clave=CONTRASEÑA_MYSQL
```

Sustituir servidor, puerto y credenciales por los entregados al grupo. Conservar los parámetros horarios: los campos `TIMESTAMP` deben convertirse correctamente entre MySQL y Java. Solo CoreServer necesita este archivo; los tres clientes acceden a sus datos mediante el servidor. El archivo está excluido de Git, así que un clon nuevo no lo trae, y las contraseñas de MySQL y de los funcionarios son credenciales diferentes.

Si CoreServer informa «No se encontró db.properties», el archivo no está en esa carpeta o CoreServer no se recompiló después de crearlo (usar **Clean and Build**). Si informa un fallo de conexión, revisar el servidor, el puerto, el usuario, la contraseña y el acceso de red a MySQL.

## 4. Cuentas de acceso

Según la exportación más reciente, la base tiene tres cuentas activas con credencial:

| Funcionario | Perfil | Asignación abierta |
| --- | --- | --- |
| `operador1` (id 1) | OPERADOR | Ventanilla 1, mixta |
| `operador2` (id 2) | OPERADOR | Ventanilla 2, solo preferenciales |
| `admin` (id 5) | ADMIN | Ninguna |

Sus contraseñas no están en el repositorio: el grupo las entrega junto con el enlace del repositorio. Si alguna se pierde, se restablece con `cr.ac.una.config.CredentialSetup` (compilar CoreServer, abrir la clase y usar **Run File**; el procedimiento está en [INICIALIZAR_ACCESO.md](database/INICIALIZAR_ACCESO.md)). La contraseña nunca se escribe en SQL ni en el repositorio.

La cuenta `admin` abre **Personas**, **Asignaciones**, **Servicios y ventanillas** y las cinco consultas. No tiene asignación de atención: para atender turnos se usa un operador.

## 5. Ejecutar las aplicaciones

Para probar todo en una sola computadora no hace falta crear `network.properties`: las aplicaciones utilizan `127.0.0.1:5000`. Si se cambia el puerto, debe coincidir en los cuatro proyectos. Para conectar otras computadoras, preparar primero [la configuración y los certificados TLS](RED_Y_TLS.md).

1. Ejecutar CoreServer con `cr.ac.una.CoreServer.App` y mantenerlo abierto. La consola debe informar el puerto sin errores de conexión a MySQL.
2. Ejecutar CostumerTurn con **Run Project**. Debe cargar Información general, Trámites y Pagos.
3. Ejecutar QueueDisplayScreen con **Run Project**. Debe indicar que está conectado y mostrar los turnos que ya estén en espera en la base.
4. Ejecutar OperatorSystem con **Run Project** e ingresar como `operador1`. Debe mostrar su asignación a la ventanilla 1.
5. Para comprobar dos funcionarios, abrir otra ejecución de OperatorSystem e ingresar como `operador2`, asignado a la ventanilla 2.

Si se modifica `db.properties` o la configuración de red, recompilar el proyecto correspondiente y reiniciar las aplicaciones afectadas. Las sesiones se pierden al reiniciar CoreServer.

## 6. Comprobar el recorrido

- Generar un turno regular y uno preferencial de Información general. La numeración continúa la que ya existe en la base: no empieza en 001.
- Llamar el siguiente desde la ventanilla 1 (mixta). La ventanilla 2 solo atiende preferenciales de los servicios del operador 2 (Información general y Trámites): si no hay ninguno en espera, responde «No hay turnos compatibles en espera.» Para probarla, generar antes un preferencial de alguno de esos dos servicios.
- En el turno llamado, usar **Volver a llamar**. Debe conservar el código y el primer llamado, aumentar su contador y actualizar la pantalla pública.
- Usar **Iniciar atención** y después **Finalizar atención**. El historial debe conservarse y la asignación debe quedar libre.
- Con una cuenta ADMIN, abrir el menú **Administración** y la vista **Consultas**, y seleccionar un rango de fechas que incluya las atenciones realizadas.
- Para la prueba de concurrencia, seguir [pruebas/README.md](pruebas/README.md).

Si el catálogo no carga, revisar la consola de CoreServer y su conexión a MySQL antes de usar **Reconectar**. Si falta acceso, comprobar que el funcionario esté activo y tenga una credencial creada. No repetir automáticamente una operación cuando se pierde su confirmación: consultar primero el estado guardado.

## Estado de la base y alcance de las comprobaciones

Los datos descritos en esta guía corresponden a la exportación más reciente y pueden haber cambiado. Un recorrido de prueba del catálogo dejó en la base una cuenta ADMIN (`qa_cat_1311_x9`), un servicio (`QXW`) y una ventanilla (931) de QA, todos inactivos, una asignación cerrada y dos turnos finalizados (QXW001 y QXW002).

El código final se comprobó con MySQL 8.0.32 local y `esquema.sql`. Sobre la base asignada (MySQL 8.4.7) se ejecutaron las aplicaciones con el código actual: emisión de un turno, llamado, rellamado, inicio y finalización de la atención. El historial de la base conserva esas operaciones. Antes se habían comprobado versiones anteriores: generación, atención, rellamado y el recorrido del catálogo. El [resumen de comprobaciones](pruebas/README.md#comprobaciones-y-límites) precisa el entorno y el alcance de cada comprobación. El despliegue entre computadoras queda como trabajo futuro.
