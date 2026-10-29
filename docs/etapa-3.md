# v0.3 — API REST y contratos HTTP

La API permite probar las cuatro vistas operativas y los seis patrones sin agregar todavía el frontend. Los controladores reciben contratos de aplicación por constructor; los DTOs traducen entradas y salidas. Las existencias persistidas siguen cambiando exclusivamente a través de `GestorInventario`.

## Inicio y herramientas

Desde la raíz del proyecto, con JDK 21 a 26 y Maven:

```sh
mvn -f backend/pom.xml verify
java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar
```

Base URL: `http://localhost:8080`. Abrir `/` muestra un índice JSON. `/api/estado` informa que el proceso está activo; no pretende ser un diagnóstico detallado de la base de datos.

También se puede usar `mvn -f backend/pom.xml spring-boot:run`.

Para cambiar el puerto: `--server.port=8081` al ejecutar el JAR, o la variable `COTO_SERVER_PORT`. El servidor escucha en `127.0.0.1` por defecto; `COTO_SERVER_ADDRESS` permite configurarlo.

Importar `backend/api/COTO-Stock.postman_collection.json` en Postman. Incluye 37 solicitudes agrupadas, comprobaciones de código HTTP y captura automática de IDs de altas. La variable de colección `baseUrl` permite cambiar el servidor.

Para correr la colección completa desde datos iniciales:

```sh
java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar \
  --coto.integraciones.automaticas=false
```

Esto desactiva el reintento periódico, pero conserva los envíos después de cada commit. La colección modifica el inventario, recibe el pedido inicial y crea productos/promociones; reiniciar H2 antes de repetirla desde cero. No debe ejecutarse como prueba no destructiva sobre una base con datos que se quieran conservar.

El archivo `backend/api/pruebas.http` contiene ejemplos equivalentes para clientes compatibles con archivos HTTP. Son operaciones independientes con comentarios sobre sus precondiciones.

## Inventario, productos y combos

| Método | Recurso | Resultado |
| --- | --- | --- |
| GET | `/api/productos` | Inventario completo; filtro opcional `tipo=SIMPLE` o `COMBO` |
| GET | `/api/productos/{id}` | Producto, contadores y disponible calculado |
| POST | `/api/productos` | Alta simple con existencias cero; 201 y Location |
| GET | `/api/combos` | Combos publicados |
| GET | `/api/combos/{id}` | Componentes directos y disponibilidad derivada |
| POST | `/api/combos` | Alta de combo con componentes simples o combos; 201 y Location |

Alta simple:

```json
{"nombre":"Yerba COTO","descripcion":"Paquete de 1 kg","precio":3500}
```

Alta de combo anidado:

```json
{
  "nombre":"Canasta COTO",
  "descripcion":"Combo ahorro y un arroz adicional",
  "precio":8000,
  "componentes":[{"stockId":6,"cantidad":1},{"stockId":1,"cantidad":1}]
}
```

Con las existencias originales, este combo requiere tres arroces y un aceite, por lo que admite 26 unidades. Los componentes no se expanden recursivamente en JSON: cada línea contiene ID, nombre, tipo, cantidad y disponible. Para inspeccionar un combo hijo se consulta su ID.

La respuesta de producto es plana. Los simples contienen `stockFisico`, `stockExhibicion` y `stockReservado`; en combos los tres valores son `null`. `componentes` es una lista vacía para simples. `precio` es el precio unitario de lista; no incluye descuentos.

## Recepción y app de sucursal

| Método | Recurso | Uso |
| --- | --- | --- |
| POST | `/api/recepcion/ingresos` | Recepción física de un producto simple |
| POST | `/api/sucursal/movimientos` | Traslados, retorno, devoluciones y mermas |
| POST | `/api/movimientos` | Registro genérico para probar los nueve tipos de movimiento |
| GET | `/api/movimientos` | Historial; filtro opcional `stockId` |
| GET | `/api/movimientos/{id}` | Movimiento por ID |
| GET | `/api/auditoria` | Auditoría de hojas y combos; filtro opcional `stockId` |

Ingreso de mercadería:

```json
{"stockId":1,"cantidad":10,"motivo":"Remito de prueba","usuario":"recepcion.demo"}
```

Movimiento de PDA:

```json
{"stockId":1,"tipo":"TRASLADO_A_GONDOLA","cantidad":5,"motivo":"Carga de góndola","usuario":"pda.demo"}
```

La ruta de sucursal admite `TRASLADO_A_GONDOLA`, `RETORNO_A_DEPOSITO`, `DEVOLUCION`, `MERMA_DEPOSITO` y `MERMA_GONDOLA`. El registro genérico admite además `RECEPCION`, `RESERVA_DIGITAL`, `CANCELACION_RESERVA` y `DESPACHO_DIGITAL`.

La fecha se toma del reloj del servidor; no se admite un campo `fecha` enviado por el cliente. `usuario` es un dato de auditoría del prototipo, no una identidad autenticada.

Un movimiento confirmado retorna `201 Created`, `Location: /api/movimientos/{id}` y:

```json
{
  "id":1000,
  "disponibilidades":{"1":76,"2":33,"6":33},
  "pedidosGenerados":[]
}
```

El ejemplo corresponde a reservar dos combos desde el fixture inicial. Los IDs generados pueden variar. `disponibilidades` incluye todos los productos observados afectados por la operación, incluidos los combos padres.

## Canal Coto Digital

| Método | Recurso | Uso |
| --- | --- | --- |
| GET | `/api/digital/catalogo` | Catálogo con disponibilidad y precio valorizado |
| POST | `/api/digital/reservas` | Reserva de producto simple o combo |
| POST | `/api/digital/cancelaciones` | Liberación de una cantidad reservada |
| POST | `/api/digital/despachos` | Retiro físico de unidades previamente reservadas |

Las tres operaciones POST reciben `stockId`, `cantidad`, `motivo` y `usuario`, y retornan un movimiento 201. Por ejemplo:

```sh
curl -i http://localhost:8080/api/digital/reservas \
  -H 'Content-Type: application/json' \
  -d '{"stockId":6,"cantidad":2,"motivo":"Compra web","usuario":"digital.demo"}'
```

La cancelación/despacho opera sobre cantidades agregadas de stock. El modelo de las versiones previas no registra una entidad de orden web ni asigna reservas a compradores. Los clientes deben evitar reenviar una operación confirmada: cada POST válido genera un nuevo movimiento y no hay una clave de idempotencia de compra.

El catálogo acepta `cantidad` (por defecto 1) y `medioPago`. La cantidad valoriza la línea homogénea; `disponible` siempre expresa unidades de producto/combo que se pueden reservar. El precio se calcula aunque la cantidad solicitada supere el disponible; la reserva posterior es la operación que valida existencias.

## Compras, políticas y pedidos de reposición

| Método | Recurso | Uso |
| --- | --- | --- |
| GET | `/api/productos/{id}/politica-reposicion` | Política persistida del artículo |
| PUT | `/api/productos/{id}/politica-reposicion` | Reemplazar/crear configuración para un stock simple |
| POST | `/api/reposicion/evaluaciones` | Evaluar todos los artículos y generar únicamente faltantes |
| GET | `/api/pedidos` | Pedidos; filtros opcionales `stockId` y `estado` |
| GET | `/api/pedidos/{id}` | Estado vigente del pedido |
| POST | `/api/pedidos/{id}/recepcion` | Recepción total de pedido enviado; retorna movimiento 201 |
| POST | `/api/pedidos/{id}/cancelacion` | Cancelar un pedido pendiente; retorna 204 sin cuerpo |

Configuración completa de ejemplo:

```json
{
  "tipo":"DEMANDA",
  "puntoPedido":0,
  "stockObjetivo":200,
  "demandaDiaria":100,
  "diasCobertura":2,
  "factorEstacional":1,
  "umbralAlerta":20,
  "destinoAlerta":"compras@coto.example"
}
```

`tipo` admite `PUNTO_PEDIDO`, `DEMANDA` o `ESTACIONAL`. Todos los parámetros se envían en el PUT, incluidos los no utilizados por la estrategia elegida. Se validan los límites del esquema SQL. Cambiar la configuración no modifica existencias ni dispara inmediatamente un pedido: se puede ejecutar una evaluación o registrar un movimiento.

Los filtros de estado aceptan `PENDIENTE`, `ENVIADO`, `RECIBIDO` o `CANCELADO`. La reposición automática sigue ejecutándose al cambiar los artículos afectados y descuenta pedidos pendientes/enviados.

Recepción de pedido:

```json
{"usuario":"recepcion.demo"}
```

Sólo se recibe un pedido `ENVIADO`. Una segunda recepción genera 409 sin volver a aumentar stock. Sólo se cancela un pedido `PENDIENTE`.

Un pedido que aparece como pendiente en la respuesta del movimiento/evaluación puede haber sido enviado por el callback posterior al commit. Consultar `/api/pedidos/{id}` para obtener el estado confirmado vigente.

## Precios y promociones

| Método | Recurso | Uso |
| --- | --- | --- |
| GET | `/api/productos/{id}/precio` | Precio de una línea; parámetros `cantidad` y `medioPago` |
| GET | `/api/productos/{id}/promociones` | Promociones persistidas del producto |
| GET | `/api/productos/{id}/promociones/{promocionId}` | Promoción individual |
| POST | `/api/productos/{id}/promociones` | Alta; 201 y Location |
| PUT | `/api/productos/{id}/promociones/{promocionId}` | Actualización completa o desactivación |

Ejemplo de alta:

```json
{"tipo":"DESCUENTO","porcentaje":12.5,"orden":10,"activa":true}
```

Tipos: `DESCUENTO`, `DOS_POR_UNO` y `MEDIO_PAGO`. Para `DOS_POR_UNO`, porcentaje debe ser cero. `MEDIO_PAGO` requiere el campo `medioPago`, por ejemplo `DEBITO`. Las otras promociones no admiten ese campo. `orden` es único dentro de cada producto; se rechaza un segundo 2x1 activo.

Prueba del Decorator:

```sh
curl 'http://localhost:8080/api/productos/1/precio?cantidad=3&medioPago=DEBITO'
```

Resultado con fixtures:

```json
{
  "productoId":1,
  "cantidad":3,
  "precioLista":5670.00,
  "precioFinal":2891.70,
  "ahorro":2778.30,
  "promocionesAplicadas":[1,2,3]
}
```

El 2x1 respeta unidades impares; los porcentajes se aplican sobre el resultado anterior. Los importes totales se redondean al final. La consulta de catálogo calcula los precios y disponibilidades en una misma unidad de lectura.

## Notificaciones, Adapter y recuperación

| Método | Recurso | Uso |
| --- | --- | --- |
| GET | `/api/notificaciones/canal` | Canal predeterminado vigente |
| PUT | `/api/notificaciones/canal` | Seleccionar `EMAIL`, `SMS` o `WHATSAPP` |
| GET | `/api/integraciones` | Outbox, disponibilidad de proveedores y llamadas simuladas |
| POST | `/api/integraciones/reintentos` | Procesar hasta 100 mensajes pendientes, en orden |
| PATCH | `/api/demo/proveedores/{sistema}` | Simular caída/recuperación; sólo perfil demo |

Cambio de canal: `{"canal":"SMS"}`. El canal se mantiene en memoria durante el proceso y los avisos pendientes conservan el canal capturado al encolarse. El destino de cada aviso se configura en la política del producto.

La consulta de integraciones informa entregas con estado, intentos y último error; mensajes; pedidos enviados al ERP; y el catálogo conocido por Coto Digital. Muestra información del prototipo mediante un puerto de aplicación, sin inyectar repositorios ni APIs externas en el controlador.

Los sistemas de simulación son `ERP`, `MENSAJERIA` y `COTO_DIGITAL`. El cuerpo del PATCH es `{"disponible":false}` o `{"disponible":true}`. Esta ruta no está registrada cuando se activa únicamente el perfil PostgreSQL. Los adaptadores siguen siendo simulados en ambos perfiles; PostgreSQL sólo cambia la persistencia.

Para comprobar una recuperación:

1. Desactivar reintentos periódicos para observar los estados de forma determinista.
2. PATCH `/api/demo/proveedores/COTO_DIGITAL` con `false`.
3. Reservar una unidad de arroz. El movimiento se confirma aunque la sincronización falle.
4. GET `/api/integraciones`: aparecen entregas pendientes con su error.
5. PATCH del mismo proveedor con `true`.
6. POST `/api/integraciones/reintentos`: se entregan las sincronizaciones en orden.
7. GET `/api/integraciones`: el catálogo refleja el último disponible y ya no hay entregas pendientes del recorrido.

El esquema FIFO, las garantías de entrega al menos una vez y sus límites están documentados en la v0.2. Los mensajes y proveedores simulados se inspeccionan en memoria; la outbox reside en SQL.

## Validación y errores

Las solicitudes no admiten propiedades desconocidas, cantidades fraccionarias en campos enteros ni omisiones de valores obligatorios. Se validan DTOs anidados, longitud de textos, precisión monetaria y parámetros de ruta/consulta. Las reglas cruzadas se validan en el dominio.

| Código HTTP | Situación |
| --- | --- |
| 200 | Consulta, configuración o evaluación completada |
| 201 | Producto, combo, promoción o movimiento creado; incluye Location consultable |
| 204 | Pedido cancelado |
| 400 | JSON inválido, validación, tipo desconocido, regla incompatible o desborde aritmético |
| 404 | Recurso inexistente o combo consultado con un ID simple |
| 405 / 415 | Método HTTP / Content-Type no admitido |
| 409 | Stock insuficiente, transición de pedido inválida o conflicto de integridad |
| 503 | Conflicto de bloqueo/concurrencia; incluye Retry-After |
| 500 | Error inesperado, con detalle genérico |

Los errores de aplicación usan RFC 9457 y `application/problem+json`. Ejemplo:

```json
{
  "type":"about:blank",
  "title":"Conflict",
  "status":409,
  "detail":"Disponible insuficiente para Aceite de girasol COTO 900 ml",
  "instance":"/api/digital/reservas",
  "codigo":"STOCK_INSUFICIENTE",
  "timestamp":"2026-10-15T20:00:00Z"
}
```

Los errores de validación agregan `errores`, con nombres de campos y mensajes. El cliente no recibe SQL ni stack traces. Los valores de ejemplo no son marcas temporales o IDs garantizados.

Las listas del prototipo son completas, sin paginación. Los filtros de listas que no encuentran coincidencias devuelven `[]`; las consultas por ID inexistente devuelven 404.

## CORS y frontend futuro

Se permiten solicitudes a `/api/**` desde `http://localhost:5173` y `http://127.0.0.1:5173`, con Content-Type JSON. Se puede ajustar `coto.web.origenes`. Los orígenes ajenos son rechazados en el preflight. No se agrega una interfaz web ni autenticación en esta versión.

## Cómo demostrar los seis patrones

| Patrón | Recorrido observable |
| --- | --- |
| Composite | Crear combo anidado, reservarlo y consultar sus hojas y disponible |
| Observer | Reservar arroz por debajo del umbral; consultar auditoría, mensajes y catálogo sincronizado |
| Singleton | Todas las rutas de modificación delegan en `GestionInventario`, cuya única implementación es la instancia clásica; las pruebas comprueban su identidad |
| Strategy | Cambiar política por producto y evaluar; cambiar canal antes de una alerta |
| Decorator | Consultar tres arroces con débito y verificar total 2891.70 |
| Adapter | Inspeccionar mensajes/ERP/Coto Digital; simular falla y reintentar la outbox |

## Verificación realizada

- 129 pruebas aprobadas sin fallos ni errores, incluidas las 86 previas y 43 pruebas HTTP completas con MockMvc, servicios, JPA y H2.
- JAR 0.3.0 empaquetado correctamente.
- Las 37 solicitudes de la colección fueron ejecutadas contra el servidor HTTP real en localhost:18080, verificando estados y variables; el recorrido terminó sin entregas pendientes.

Referencias técnicas: [validación MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html), [respuestas de error](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) y [MockMvc en Spring Boot](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.html).

La [v0.4](etapa-4.md) agrega React, el Design System COTO y las cuatro vistas.
