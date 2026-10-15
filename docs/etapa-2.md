> Documentación de la Etapa 2. La API HTTP y los comandos de la versión vigente se encuentran en el README y en etapa-3.md.

# Etapa 2 — Aplicación, persistencia e integración

## Alcance

La entrega implementa los servicios de aplicación, el Singleton clásico, repositorios JPA y Adapter para ERP, mensajería y Coto Digital. El dominio de la Etapa 1 conserva sus patrones e invariantes. No se agregan controladores REST ni frontend.

El arranque está en `StockApplication`. `ConfiguracionAplicacion` conecta los contratos con sus implementaciones. Se utiliza Spring Boot 4.1.1, Java 21, Hibernate y SQL definido explícitamente; Hibernate valida el esquema y no lo genera.

## Singleton y contratos de aplicación

`GestorInventario` contiene exactamente una instancia estática privada, un constructor privado y `getInstancia()`. El método `configurar(Dependencias)` conecta una vez sus colaboradores. El bean de Spring devuelve esa misma instancia, algo comprobado por identidad de referencia y acceso concurrente en las pruebas. El cierre del contexto libera la configuración; nunca cambia la identidad del objeto. Se admite un contexto de aplicación activo por JVM.

Los futuros controladores dependerán de `GestionInventario`. Los métodos que cambian existencias persistidas pertenecen a este coordinador:

| Método | Caso de uso |
| --- | --- |
| `registrarMovimiento(m)` | Recepción, góndola, retornos, devoluciones, mermas, reservas, cancelaciones y despachos |
| `evaluarReposicion()` | Evaluación explícita de todas las políticas y del stock inicialmente bajo |
| `recibirPedido(id, usuario)` | Transición de pedido enviado a recibido y recepción física en una única transacción |
| `cancelarPedido(id)` | Cancelación de un pedido que todavía no fue enviado |
| `crearProducto(datos)` | Alta de producto simple con existencias cero |
| `crearCombo(datos, componentes)` | Alta de un combo publicado, validando todas sus referencias |
| `configurarPolitica(politica)` | Configuración persistida de estrategia y umbral para un producto simple |
| `guardarPromocion(promocion)` | Alta/actualización de promoción, incluyendo desactivación |

También hay consultas de inventario, promociones, movimientos, auditoría y pedidos. Devuelven modelos del dominio reconstruidos, nunca entidades JPA ni objetos administrados por el EntityManager. Mutar una copia consultada no modifica SQL.

Los tipos `AltaProducto`, `ComponenteCombo`, `PedidoRegistrado` y `MovimientoRegistrado` son valores/contratos internos. Los DTOs HTTP se definirán en la Etapa 3.

## Una transacción por movimiento

1. `UnidadDeTrabajo` abre la transacción. Su implementación utiliza `TransactionTemplate`, de modo que el Singleton no necesita proxies de anotaciones para ser transaccional.
2. El repositorio bloquea la fila de coordinación del inventario con `PESSIMISTIC_WRITE`. Esto también serializa las altas y cambios de composición, evitando cargas inconsistentes o inserciones fantasma dentro de una operación.
3. Se reconstruye todo el grafo, con una sola instancia por ID. No hay inventario mutable compartido entre transacciones.
4. Se conectan los tres observadores. El auditor recibe una línea de base sin generar registros artificiales; las alertas recuperan su estado confirmado en SQL.
5. El dominio valida y aplica el movimiento completo. Un combo sólo admite reserva, cancelación o despacho; la recepción/merma/góndola operan sobre artículos simples.
6. Se guardan contadores de hojas, movimiento, auditoría correlacionada, estado de alertas y eventos de integración. Los observadores acumulan información local; no llaman sistemas externos dentro de la mutación del dominio.
7. Se evalúa reposición para los productos afectados y se encolan los pedidos nuevos para el ERP.
8. Se confirma la transacción. Sólo entonces se intenta entregar la outbox, utilizando una nueva transacción.

Si cualquier paso anterior al commit falla, se descartan también las instancias de dominio modificadas. La prueba de rollback tardío aborta después de guardar todas las entidades y demuestra que no quedan existencias, movimientos, pedidos, auditorías ni mensajes nuevos.

Se eligió un bloqueo grueso para el prototipo de una sucursal: simplifica la atomicidad de combos anidados que comparten productos. Los registros `stock` también tienen `@Version`. Un sistema con mayor carga puede reemplazar la coordinación global por bloqueos ordenados de los componentes afectados sin cambiar los contratos del dominio.

## Observer conectado a persistencia

- `AuditorMovimientos` toma el disponible anterior desde una línea de base del grafo cargado. Cada evento conserva el estado previo y final. Si un combo recibe varias propagaciones de la misma operación, se consolida en una fila por stock/movimiento sin perder el primer valor anterior.
- `SincronizadorCotoDigital` acumula la disponibilidad final y la convierte en un evento de outbox por producto afectado, incluidos los combos padres.
- `AlertaStockBajo` conserva un aviso por episodio. `restaurarEstado` recupera si ya se avisó y `politica_reposicion.alerta_activa` sólo se modifica dentro de la transacción de inventario.

El estado de alerta representa **aviso encolado**, no necesariamente entregado. Una entrega fallida sigue pendiente en la outbox y no genera avisos repetidos por cada movimiento. Al recuperarse el stock hasta el umbral se rearma el episodio. `evaluarReposicion()` detecta también los productos que estaban bajos al iniciar el sistema.

## Strategy: reposición

`ServicioReposicion` es el Context explícito. Expone `setEstrategia(e)` y `generarPedido(s)` para seleccionar una política en forma directa. Si no hay necesidad, `generarPedido` lanza una excepción de estado; la operación automática usa `planificar`, que retorna `Optional.empty()`.

Para procesar distintos productos sin competir por una estrategia global mutable, el gestor llama a `planificar(stock, politica.crearEstrategia(), pendiente)` con la política correspondiente a ese SKU.

| Política persistida | ConcreteStrategy |
| --- | --- |
| `PUNTO_PEDIDO` | `ReposicionPuntoDePedido` |
| `DEMANDA` | `ReposicionPorDemanda` |
| `ESTACIONAL` | `ReposicionEstacional` |

Se descuenta la cantidad ya cubierta por pedidos `PENDIENTE` y `ENVIADO`. Sólo se persiste un pedido si queda una cantidad positiva. Las evaluaciones repetidas no duplican una necesidad ya cubierta. Si una nueva reserva aumenta la necesidad, se puede crear un pedido adicional únicamente por la diferencia.

El pedido queda `PENDIENTE` hasta la aceptación del ERP simulado. Luego pasa a `ENVIADO`; la recepción completa lo lleva a `RECIBIDO`. No hay recepción parcial en el prototipo. El gestor valida las transiciones antes de cambiar stock, por lo que una segunda recepción del mismo pedido no ingresa mercadería nuevamente. Un pedido cancelado antes de su despacho se omite al consumir su evento pendiente.

Los resultados de `registrarMovimiento` describen el momento de su transacción. Un pedido generado puede figurar pendiente en ese resultado aunque el despacho posterior ya lo haya enviado; `listarPedidos()` consulta el estado confirmado vigente.

Las reservas y sus cancelaciones operan sobre cantidades agregadas del artículo. La identificación de clientes y órdenes de compra web no forma parte del alcance actual.

## Strategy: notificaciones

`ServicioNotificaciones` implementa `EmisorAlertas` y expone `enviar(AlertaStockBajo)` y selección de canal. Cada ConcreteStrategy recibe el proveedor adecuado:

| Canal | Strategy | Adapter |
| --- | --- | --- |
| EMAIL | `EmailNotificador` | `AdaptadorProveedor(api, "EMAIL")` |
| SMS | `SmsNotificador` | `AdaptadorProveedorSMS(api)` |
| WHATSAPP | `WhatsAppNotificador` | `AdaptadorProveedor(api, "WHATSAPP")` |

La outbox captura canal, destino y texto al crearse el aviso. Cambiar el canal predeterminado posteriormente no modifica un mensaje pendiente. Los destinos de ejemplo usan el dominio reservado `.example`. Para las simulaciones SMS/WhatsApp se puede configurar otro destino en la política.

## Decorator en el servicio de precios

`ServicioValorizacion.valorizar(productoId, cantidad, medioPago)` carga las promociones persistidas y arma la cadena sobre `PrecioLista` según `orden`:

- `DESCUENTO` agrega `DescuentoDecorator`.
- `DOS_POR_UNO` agrega `PromocionDecorator("2x1")` respetando pares y unidades impares.
- `MEDIO_PAGO` agrega `PromocionDecorator("Descuento Medio de Pago", porcentaje)` sólo cuando el medio indicado coincide, sin distinguir mayúsculas.

Las promociones inactivas se omiten. Se rechaza un segundo 2x1 activo para el mismo producto. Se permite acumular descuentos diferentes. El total se redondea al final a dos decimales con `HALF_UP`. Se retorna precio de lista, precio final, ahorro e IDs de promociones aplicadas. El producto y su precio base permanecen intactos.

Con los fixtures, tres arroces cuestan `5670 → 3780 → 3402 → 2891,70` al aplicar 2x1, 10% y 15% con débito. Sin el medio correspondiente, no se aplica el último descuento.

## Adapter explícito

| Target del núcleo | Adapter | Adaptee y firma incompatible |
| --- | --- | --- |
| `ProveedorMensajeria.enviarMensaje(destino, texto)` | `AdaptadorProveedor` | `ApiProveedorExterno.send(canal, to, body)` |
| Mismo Target, canal SMS | `AdaptadorProveedorSMS` | La misma API configurada para SMS |
| `IntegracionERP.enviarPedido(pedido)` | `AdaptadorERP` | `SistemaERPExterno.submitPurchaseOrder(reference, sku, units, date)` |
| `PublicadorDisponibilidad.sincronizar(id, disponible)` | `AdaptadorCotoDigital` | `ApiCotoDigitalExterna.updateInventory(sku, units)` con valores String |

Las APIs externas simuladas registran llamadas y permiten `setDisponible(false)` para comprobar fallas. No realizan conexiones externas reales. El ERP utiliza una referencia estable `COTO-<pedidoId>` y deduplica reintentos.

## Outbox y recuperación

`evento_integracion` guarda tipos `STOCK_DIGITAL`, `ALERTA` y `PEDIDO_ERP`, una clave única, el contenido, estado de entrega, cantidad de intentos y último error. El contenido se registra en la misma transacción que lo origina.

`ServicioIntegraciones.procesarPendientes()` se puede ejecutar manualmente y también se invoca después del commit. Una tarea opcional reintenta cada cinco segundos. Para deshabilitar únicamente esa tarea: `--coto.integraciones.automaticas=false`.

Cada lote procesa como máximo 100 eventos en orden de ID, bajo un bloqueo SQL exclusivo de envíos y el bloqueo del inventario. Este último protege las transiciones de pedidos frente a una cancelación o recepción concurrente. La primera falla detiene el lote, conserva el evento pendiente y registra el error: se evita que una sincronización vieja sobrescriba una nueva. En este prototipo, un proveedor caído puede demorar eventos de los otros canales; separar colas por destino sería una mejora para mayor volumen.

La entrega es **al menos una vez**: si un proveedor acepta el envío y el proceso cae antes de confirmar su estado, habrá un reintento. El ERP simulado es idempotente por referencia y el inventario digital aplica valores absolutos. La API de mensajes solicitada no recibe una clave de idempotencia y por eso un SMS/email/WhatsApp podría repetirse en esa ventana. No se promete entrega exactamente una vez.

## Mapeo JPA y SQL

Los modelos bajo `repository.entity` son representaciones relacionales separadas. `StockEntity` usa el discriminador explícito `tipo`; el repositorio lo traduce a `StockSimple` o `ComboStock`. La única tabla `stock` y `linea_combo` conservan el diseño solicitado, sin anotar el dominio ni crear tablas separadas de subtipos.

`ProductoEntity` genera el ID, y `StockEntity` comparte esa PK con `@MapsId`. Las identidades comienzan en 1000 para no colisionar con el fixture. El repositorio carga el catálogo y después enlaza componentes por ID; la construcción valida ciclos y reutiliza las hojas compartidas.

Las nuevas tablas son:

| Tabla | Responsabilidad |
| --- | --- |
| `politica_reposicion` | Estrategia, parámetros, umbral, destino y episodio de alerta por stock simple |
| `promocion` | Descuentos y promociones por producto, orden, vigencia booleana y medio de pago |
| `evento_integracion` | Outbox transaccional recuperable |
| `bloqueo_operacion` | Dos filas técnicas de coordinación para inventario y envíos |

Los valores `disponible` de auditoría/outbox son snapshots históricos o mensajes, nunca la fuente del disponible de inventario. Los contadores de combos permanecen `NULL`.

El perfil demo inicializa H2 temporal; PostgreSQL se configura mediante variables de entorno y requiere inicialización manual única. El archivo `db/upgrade-etapa2.sql` permite actualizar el esquema universitario de la Etapa 1 sin modificar sus registros originales. No se utiliza Flyway ni se ejecuta este upgrade automáticamente.

## Pruebas y ejecución

Desde la raíz: `mvn -f backend/pom.xml verify`.

La suite verifica dominio, contextos de Strategy/Adapter, Singleton real, carga JPA, movimiento de combos, propagación de auditorías, rollback tardío, concurrencia SQL, avisos persistidos, orden FIFO, fallas y recuperación de integraciones, pedidos, catálogo, promociones y upgrade de esquema. Los detalles de ejecución y perfiles están en el README.

Referencias técnicas utilizadas: [Spring Boot: requisitos](https://docs.spring.io/spring-boot/system-requirements.html) y [Spring: transacciones programáticas](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html).

## Próximas etapas

La Etapa 3 agregará controladores REST, DTOs y manejo HTTP de errores sobre los contratos de aplicación existentes. La Etapa 4 agregará React y las cuatro vistas con el Design System COTO. Ambas requieren confirmación.
