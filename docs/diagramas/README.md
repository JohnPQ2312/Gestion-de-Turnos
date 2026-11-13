# Diagramas del sistema

Referencia: código y esquema de `cc9405e`. Los diagramas son una base actual para la documentación; deben contrastarse al integrar servicios y ventanillas.

| Diagrama | Vista ampliable | Imagen | Editable |
| --- | --- | --- | --- |
| Componentes y conexiones | [SVG](componentes.svg) | [PNG](componentes.png) | [drawio](componentes.drawio) |
| Modelo relacional | [SVG](base_datos.svg) | [PNG](base_datos.png) | [drawio](base_datos.drawio) |

Para editar, abrir el archivo `.drawio` desde el dispositivo en diagrams.net. Cada archivo contiene una sola página. Después de editar, actualizar sus exportaciones SVG y PNG y comprobar que coincidan. El SVG permite ampliar sin perder nitidez; el PNG sirve como imagen para documentos y presentaciones.

## Cómo leerlos

En componentes, las flechas continuas indican el inicio de una operación y las respuestas vuelven por el mismo canal. La discontinua representa el canal de avisos hacia la pantalla pública. No existe conexión directa entre los clientes y MySQL.

En base de datos, las multiplicidades junto a cada tabla describen cuántas filas de esa tabla pueden vincularse a una fila del extremo opuesto. `1` es obligatorio, `0..1` es opcional y `0..*` permite ninguna o muchas. Las tablas puente resuelven servicios múltiples; la asignación registra quién ocupó la ventanilla y durante qué intervalo. El puente en un cruce de líneas indica que no se unen.

La relación histórica de muchos tickets por asignación no permite atenderlos al mismo tiempo: el índice único de la columna calculada limita a uno el turno activo. Las columnas calculadas no agregan relaciones foráneas.

El diagrama incluye las diez tablas y once FK del [DDL](../../database/script%20borrador.sql), con nombres y tipos de columna, PK, FK y UQ de una columna. El SQL conserva la especificación completa de nulabilidad, valores predeterminados, índices compuestos y CHECK. No se representa una tabla de eventos de rellamado porque no existe en el esquema actual.

La explicación por tabla, relación y restricción está en los apartados 6 y 7 del [documento técnico](../documento_tecnico.md).
