# Recepción de pedidos: falso conflicto de integridad en H2

El perfil demo usaba H2 2.4.240. Esa versión tiene una regresión al evaluar restricciones `CHECK(columna IN (...))` desde otra conexión después de cerrar la conexión que creó el esquema. El motor conserva una referencia a la sesión cerrada y rechaza escrituras válidas.

En este proyecto se manifestó como `SQLState 23514`, `Check constraint invalid: CONSTRAINT_A8C`, al insertar en `movimiento_stock`. Esa restricción valida los tipos de movimiento admitidos; `RECEPCION` sí es válido. La causa interna reproducida fue `The database has been closed [90098-240]`, aunque la base en memoria y otras conexiones seguían disponibles.

El servidor original arrancó a las 18:19 y registró el primer fallo a las 18:49. El intervalo es consistente con la renovación de conexiones del pool. Las pruebas anteriores abrían instancias nuevas y no retiraban la conexión de inicialización, por eso no reproducían el problema.

La transacción de recepción se revierte al fallar: el pedido conserva su estado anterior y el ingreso no incrementa las existencias. El mensaje HTTP genérico de integridad ocultaba el origen del fallo del motor.

## Corrección y verificación

Se actualizó únicamente la dependencia H2 a **2.5.252**, conservando todas las restricciones SQL. La corrección está incluida desde la rama 2.5:

- [Incidencia H2 #4302](https://github.com/h2database/h2database/issues/4302).
- [Corrección #4311](https://github.com/h2database/h2database/pull/4311).
- [Versiones oficiales de H2](https://github.com/h2database/h2database/releases).

Se agregaron dos pruebas de regresión:

1. `SchemaTest.recepcionValidaTrasCerrarLaConexionQueCreoLosChecks`: cierra la conexión que ejecutó el esquema y los fixtures, abre otra, registra una recepción válida y verifica que un tipo inexistente siga siendo rechazado con `23513`. La prueba falla con `23514` en H2 2.4.240 y pasa en 2.5.252.
2. `AplicacionIntegracionTest.recepcionFuncionaDespuesDeRenovarLasConexionesDelPool`: retira las conexiones físicas de Hikari y recibe un pedido mediante el servicio real. Verifica disponible, estado recibido, un único movimiento y rechazo de una segunda recepción.

Verificación del 15/10/2026 con Java 26: `mvn -f backend/pom.xml verify`, **131 pruebas aprobadas, cero fallos y errores**, JAR empaquetado.

## Cargar la corrección

El proceso Java ya iniciado continúa usando la dependencia anterior. Detenerlo con Ctrl+C y arrancar desde la raíz:

```sh
mvn -f backend/pom.xml spring-boot:run
```

El perfil demo utiliza H2 en memoria: al detener el backend se pierden los cambios de prueba y al arrancar se recrean los datos iniciales. No se reinició automáticamente el servidor habitual de desarrollo. La actualización de dependencia no cambia el esquema ni el perfil PostgreSQL.
