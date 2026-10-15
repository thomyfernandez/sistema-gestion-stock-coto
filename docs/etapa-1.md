> Documento histórico de la Etapa 1. La versión vigente del proyecto se describe en el README de la raíz y en etapa-2.md.

# Sistema de Gestión de Stock — COTO C.I.C.S.A.

Trabajo Práctico Integrador. **Entrega: Etapa 1**, con dominio Java puro, modelo SQL y pruebas ejecutables. Los datos comerciales son ficticios.

El proyecto representa **una única sucursal**. No existe una entidad ni una tabla `Sucursal`. Spring Boot se incorporará al implementar los servicios y la persistencia en la Etapa 2; esta entrega no inicia un servidor HTTP.

## Ejecutar la entrega

Requisitos: JDK 21 o superior y Maven 3.9 o superior.

Desde la raíz del repositorio:

```sh
mvn -f backend/pom.xml test
```

Para compilar, probar y generar el JAR de dominio:

```sh
mvn -f backend/pom.xml verify
```

El JAR contiene el dominio y los scripts SQL; no es una aplicación ejecutable. Las pruebas ejecutan los scripts sobre H2 en modo PostgreSQL, reconstruyen el Composite y contrastan los movimientos con las existencias iniciales. H2 y JUnit sólo están en el classpath de pruebas. Maven necesita acceso a su repositorio la primera vez; con las dependencias completas en caché admite `-o`.

## Estructura del backend

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/ar/com/coto/stock/
    │   │   ├── controller/package-info.java
    │   │   ├── dto/package-info.java
    │   │   ├── service/package-info.java
    │   │   ├── repository/package-info.java
    │   │   ├── adapter/package-info.java
    │   │   └── domain/
    │   │       ├── model/
    │   │       │   ├── Producto.java
    │   │       │   ├── Stock.java
    │   │       │   ├── StockSimple.java
    │   │       │   ├── ComboStock.java
    │   │       │   ├── LineaCombo.java
    │   │       │   ├── MovimientoStock.java
    │   │       │   ├── TipoMovimiento.java
    │   │       │   ├── PedidoReposicion.java
    │   │       │   ├── EstadoPedido.java
    │   │       │   ├── AuditoriaStock.java
    │   │       │   ├── StockInsuficienteException.java
    │   │       │   └── Reglas.java
    │   │       ├── observer/
    │   │       │   ├── ObservadorStock.java
    │   │       │   ├── AlertaStockBajo.java
    │   │       │   ├── SincronizadorCotoDigital.java
    │   │       │   └── AuditorMovimientos.java
    │   │       ├── reposicion/
    │   │       │   ├── EstrategiaReposicion.java
    │   │       │   ├── ReposicionPuntoDePedido.java
    │   │       │   ├── ReposicionPorDemanda.java
    │   │       │   └── ReposicionEstacional.java
    │   │       ├── notificacion/
    │   │       │   ├── CanalNotificador.java
    │   │       │   ├── EmailNotificador.java
    │   │       │   ├── SmsNotificador.java
    │   │       │   └── WhatsAppNotificador.java
    │   │       ├── valorizacion/
    │   │       │   ├── ItemValorizable.java
    │   │       │   ├── PrecioLista.java
    │   │       │   ├── DecoradorPrecio.java
    │   │       │   ├── DescuentoDecorator.java
    │   │       │   └── PromocionDecorator.java
    │   │       └── port/
    │   │           ├── EmisorAlertas.java
    │   │           ├── PublicadorDisponibilidad.java
    │   │           ├── RegistroAuditoria.java
    │   │           └── ProveedorMensajeria.java
    │   └── resources/
    │       ├── schema.sql
    │       └── data.sql
    └── test/java/ar/com/coto/stock/
        ├── domain/
        │   ├── CompositeTest.java
        │   ├── MovimientosTest.java
        │   ├── ObserverTest.java
        │   ├── EstrategiasTest.java
        │   └── DecoradoresTest.java
        └── persistence/SchemaTest.java
```

Los `package-info.java` documentan los paquetes de las siguientes etapas. No contienen implementaciones anticipadas de servicios, adaptadores ni endpoints.

## Separación de las cuatro capas

| Capa | Responsabilidad | Entrega |
| --- | --- | --- |
| Presentación: `controller`, `dto` | HTTP y validación de contratos; acceso a interfaces de casos de uso | Etapa 3 |
| Aplicación: `service` | Orquestación, `GestorInventario`, selección de estrategias y transacciones | Etapa 2 |
| Dominio: `domain` | Invariantes, entidades, Composite, Observer, estrategias y decoradores | Etapa 1 |
| Persistencia e integración: `repository`, `adapter` | SQL/JPA y traducción de contratos externos | Etapa 2 |

Las colaboraciones externas del dominio se expresan mediante interfaces en `domain.port`. De esa manera `AlertaStockBajo` puede enviar a `ServicioNotificaciones` mediante `EmisorAlertas`, sin importar una clase de aplicación. Los futuros repositorios/adaptadores implementarán los contratos correspondientes. El dominio no importa Spring, JPA, JDBC ni clases de las otras capas.

La separación por capas describe el flujo de responsabilidad. La dirección de dependencias de código respeta inversión de dependencias: la infraestructura implementa contratos del núcleo. El dominio no dependerá de implementaciones JPA para acceder a la cuarta capa.

En la Etapa 2, `GestorInventario` será la única entrada pública a casos de uso que cambien existencias. En esta entrega se prueban directamente los métodos de las entidades: no hay una entrada de aplicación alternativa al gestor.

## Modelo relacional

Ejecutar primero `backend/src/main/resources/schema.sql` y después `data.sql` sobre una base vacía. No son scripts de migración ni de reinicio destructivo. En PostgreSQL, con una base local ya creada:

```sh
psql -v ON_ERROR_STOP=1 -1 -d coto_stock \
  -f backend/src/main/resources/schema.sql \
  -f backend/src/main/resources/data.sql
```

| Tabla | Contenido e integridad |
| --- | --- |
| `producto` | ID, nombre, descripción y precio `NUMERIC(15,2)` |
| `stock` | PK compartida con `producto`; discriminador `SIMPLE`/`COMBO`; contadores sólo para simples; versión para concurrencia futura |
| `linea_combo` | Combo, componente y cantidad; PK compuesta; el padre debe ser tipo `COMBO`; permite hijos simples o combos |
| `movimiento_stock` | Tipo, cantidad positiva, fecha, motivo y usuario |
| `pedido_reposicion` | Stock simple, fecha, cantidad positiva y estado |
| `auditoria_stock` | Snapshots históricos de disponibilidad y referencia opcional al movimiento |

`stock` no almacena disponibilidad. Sus restricciones exigen `físico >= exhibición + reservado`, contadores no negativos y los tres contadores `NULL` para combos. La PK compartida significa que `stock.id = producto.id`, también utilizado por `MovimientoStock.stockId` y `PedidoReposicion.stockId`.

El SQL evita componentes inexistentes, padres simples, duplicados y autorreferencias directas. Los ciclos indirectos se validan al construir el grafo de dominio; no se implementan triggers dependientes de un motor. Un combo vacío puede existir mientras se construye, tiene disponible cero y no admite reservas.

`auditoria_stock.disponible_actual` es información histórica, no una fuente de existencias. La consulta del disponible vigente siempre utiliza el dominio. Las identidades generadas empiezan en 1000 para no colisionar con los IDs explícitos de las muestras.

La sintaxis apunta a PostgreSQL y se prueba automáticamente con H2 en su modo de compatibilidad. Esa prueba no reemplaza una verificación contra PostgreSQL real al incorporar persistencia.

Datos incluidos:

| ID | Producto | Físico | Exhibición | Reservado | Disponible calculado |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | Arroz COTO 1 kg | 120 | 30 | 10 | 80 |
| 2 | Aceite COTO 900 ml | 55 | 15 | 5 | 35 |
| 3 | Leche COTO 1 l | 48 | 24 | 18 | 6 |
| 4 | Bananas por kg entero | 28 | 8 | 4 | 16 |
| 5 | Pan dulce COTO 500 g | 40 | 10 | 0 | 30 |
| 6 | Combo Ahorro: 2 arroces + 1 aceite | — | — | — | 35 |

También se incluyen 14 movimientos de apertura, un pedido pendiente de 30 leches y seis registros de auditoría. No se deben reaplicar esos movimientos sobre los contadores ya cargados: el historial explica el snapshot inicial. La prueba de reconstrucción los aplica sobre stocks en cero y verifica su correspondencia.

## Patrones GoF de esta etapa

| Patrón | Roles explícitos |
| --- | --- |
| Composite | Component `Stock`; Leaf `StockSimple`; Composite `ComboStock`; relación cuantificada `LineaCombo` |
| Observer | Subject `Stock`; Observer `ObservadorStock`; concretos `AlertaStockBajo`, `SincronizadorCotoDigital`, `AuditorMovimientos` |
| Strategy: reposición | Strategy `EstrategiaReposicion`; concretas `ReposicionPuntoDePedido`, `ReposicionPorDemanda`, `ReposicionEstacional` |
| Strategy: notificaciones | Strategy `CanalNotificador`; concretas `EmailNotificador`, `SmsNotificador`, `WhatsAppNotificador` |
| Decorator | Component `ItemValorizable`; ConcreteComponent `PrecioLista`; Decorator `DecoradorPrecio`; concretos `DescuentoDecorator`, `PromocionDecorator` |

Los dos casos de Strategy constituyen un mismo patrón. Sus contextos `ServicioReposicion` y `ServicioNotificaciones` corresponden a la Etapa 2. `ProveedorMensajeria` es únicamente el contrato Target necesario para conectar las estrategias: la implementación de Adapter y el Singleton explícito también quedan para esa etapa.

### Composite y reglas de movimientos

`StockSimple.getDisponible()` calcula `stockFisico - stockExhibicion - stockReservado`. El físico representa todas las unidades que siguen en la sucursal, incluidas góndola y reservas. No se admite que góndola y reservas se superpongan.

| Movimiento | Físico | Exhibición | Reservado |
| --- | --- | --- | --- |
| Recepción / devolución apta para venta | +cantidad | Igual | Igual |
| Traslado a góndola | Igual | +cantidad | Igual |
| Retorno a depósito | Igual | −cantidad | Igual |
| Merma en depósito | −cantidad | Igual | Igual |
| Merma en góndola | −cantidad | −cantidad | Igual |
| Reserva digital | Igual | Igual | +cantidad |
| Cancelación de reserva | Igual | Igual | −cantidad |
| Despacho digital | −cantidad | Igual | −cantidad |

Las operaciones validan la zona de origen antes de modificarla. Las mermas de depósito y los traslados no consumen unidades comprometidas. Las devoluciones del prototipo representan mercadería apta que vuelve al depósito; la mercadería descartada se registra como merma.

En combos con componentes independientes, la disponibilidad es `min(disponibleComponente / cantidadEnCombo)`, con división entera. Si diferentes ramas contienen el mismo artículo, primero se suman las cantidades requeridas por unidad de combo: así no se cuenta dos veces una misma existencia. Todas las ramas deben reutilizar la misma instancia de un stock identificado; el futuro repositorio debe reconstruir el grafo usando un mapa por ID.

Una reserva, cancelación o despacho de un combo valida el plan completo, modifica las hojas y recién entonces notifica. Una operación rechazada no deja cambios parciales. El monitor compartido protege el modelo en una JVM; las transacciones y el control de concurrencia SQL siguen siendo responsabilidad de la Etapa 2.

Ejemplo ejecutable en JShell, tras compilar, con `jshell --class-path backend/target/classes`:

```java
import ar.com.coto.stock.domain.model.*;

Producto arroz = new Producto(1, "Arroz COTO", "1 kg", 1890);
Producto aceite = new Producto(2, "Aceite COTO", "900 ml", 3290);
Producto promocion = new Producto(6, "Combo Ahorro", "Dos arroces y un aceite", 6490);
StockSimple stockArroz = new StockSimple(arroz, 120, 30, 10);
StockSimple stockAceite = new StockSimple(aceite, 55, 15, 5);
ComboStock combo = new ComboStock(promocion);
combo.agregar(stockArroz, 2);
combo.agregar(stockAceite, 1);
System.out.println(combo.getDisponible()); // 35
combo.reservar(3);
System.out.println(stockArroz.getStockReservado()); // 16
System.out.println(stockAceite.getStockReservado()); // 8
System.out.println(combo.getDisponible()); // 32
```

### Observer y puertos de salida

`Stock` permite suscribir y quitar observadores. `ComboStock` también observa a sus componentes: una reserva individual de arroz actualiza la disponibilidad del combo y propaga el evento a sus propios observadores.

`AlertaStockBajo` notifica cuando el disponible es **menor** al umbral. Avisa una vez por episodio y se rearma al recuperar el umbral. Un envío fallido podrá reintentarse con el próximo cambio. Conserva un `Aviso` inmutable que el futuro servicio debe capturar antes de una entrega asíncrona.

`SincronizadorCotoDigital` delega en `PublicadorDisponibilidad`. `AuditorMovimientos` delega en `RegistroAuditoria`, con un `Clock` inyectable. El disponible anterior es `null` para la primera observación; se puede realizar una llamada inicial a `actualizar` al suscribir para establecer una línea de base. También audita despachos y mermas en góndola, donde el disponible puede conservarse aunque cambie el físico.

Una excepción de un observador se registra y no impide avisar a los restantes ni revierte el stock ya modificado. Todavía no hay garantías de entrega durable o coordinación con un commit SQL: se definirán con los servicios de la Etapa 2. Un grafo con varios caminos puede propagar más de una notificación con el mismo estado final; las integraciones deberán ser idempotentes.

Ejemplo adicional para la misma sesión de JShell:

```java
import ar.com.coto.stock.domain.observer.*;

AlertaStockBajo alerta = new AlertaStockBajo(75, "compras@coto.example",
        a -> System.out.println(a.getUltimoAviso().getTexto()));
stockArroz.agregarObservador(alerta);
stockArroz.agregarObservador(new SincronizadorCotoDigital(
        (id, disponible) -> System.out.println("Coto Digital: " + id + " = " + disponible)));
stockArroz.agregarObservador(new AuditorMovimientos(System.out::println));
stockArroz.reservar(1); // Disponible 73; dispara los tres observadores.
```

### Strategy

Las políticas usan disponible, no físico. Sus parámetros son explícitos y validados:

| Estrategia | Cálculo de cantidad |
| --- | --- |
| Punto de pedido | Si disponible ≤ punto de pedido: objetivo − disponible; en otro caso 0 |
| Por demanda | max(0, ceil(demanda diaria × días de cobertura) − disponible) |
| Estacional | max(0, ceil(objetivo base × factor estacional) − disponible) |

Una cantidad cero significa que el futuro contexto no debe crear un pedido. `PedidoReposicion` exige cantidad positiva y protege la fecha con copias defensivas. Sus estados admiten `PENDIENTE → ENVIADO → RECIBIDO` o `PENDIENTE → CANCELADO`. Marcar recibido no altera existencias por sí mismo: el gestor coordinará el movimiento de recepción.

Las políticas aceptan `Stock` como indica el contrato; los pedidos a proveedores corresponden a hojas simples. El futuro servicio evaluará los componentes, descontará pedidos pendientes según la política elegida y evitará pedidos duplicados; no se implementa esa orquestación en esta etapa.

Las tres estrategias de canal dependen de `ProveedorMensajeria`, nunca de una API externa. En la Etapa 2 cada una recibirá un adaptador configurado para su canal (`EMAIL`, `SMS` o `WHATSAPP`). Aquí se pueden ejecutar con una lambda que registre los mensajes, como lo hacen las pruebas.

### Decorator y promociones acumulables

El contrato conserva `getPrecio(): double`. Para una unidad, `PrecioLista(producto)` devuelve el precio base. La sobrecarga `PrecioLista(producto, cantidad)` representa una línea homogénea y devuelve su total. `getCantidad()` se propaga por los decoradores para aplicar promociones por unidades.

Un 2x1 cobra `cantidad - cantidad / 2`: una unidad paga una, dos pagan una y tres pagan dos. No se aplica un 50% indiscriminado a cantidades impares. El descuento por medio de pago requiere indicar explícitamente su porcentaje.

```java
import ar.com.coto.stock.domain.valorizacion.*;

Producto productoPrecio = new Producto(20, "Producto de ejemplo", "Demostración de promociones", 1000);
ItemValorizable precio = new PrecioLista(productoPrecio, 3); // 3000
precio = new PromocionDecorator(precio, "2x1"); // 2000
precio = new DescuentoDecorator(precio, 10); // 1800
precio = new PromocionDecorator(precio, "Descuento Medio de Pago", 15); // 1530
System.out.println(precio.getPrecio());
```

Los porcentajes se aplican sobre el resultado envuelto: 10% seguido de 20% deja 72% del precio original. Se utiliza `BigDecimal` en las operaciones internas; se conserva `double` en la interfaz requerida. La política de redondeo monetario a dos decimales se centralizará en `ServicioValorizacion` en la Etapa 2. El precio base del producto nunca se modifica.

## SOLID y GRASP

- **Experto en información y alta cohesión:** `StockSimple` protege sus contadores; `ComboStock` conoce su composición; cada estrategia contiene una política; cada decorador transforma un precio.
- **Responsabilidad única:** las entidades no persisten, no envían HTTP y no coordinan casos de uso. `AuditorMovimientos` crea el registro; el puerto decide dónde guardarlo.
- **Polimorfismo y sustitución:** simples y combos responden a `Stock`; los clientes de valoración trabajan con `ItemValorizable`; las políticas se intercambian mediante interfaces.
- **Abierto/cerrado e interfaces pequeñas:** se pueden agregar estrategias, observadores y decoradores sin modificar a sus clientes. Los puertos tienen una sola responsabilidad.
- **Bajo acoplamiento e inversión de dependencias:** los colaboradores se inyectan por constructor y las salidas del dominio se expresan como contratos propios.

## Verificación y siguientes entregas

La suite cubre disponibilidad, contadores por tipo de movimiento, invariantes, combos anidados, componentes compartidos, ciclos, reservas fallidas sin efectos parciales, concurrencia, observadores, políticas, transiciones de pedidos, acumulación de promociones y restricciones SQL.

Verificación de esta entrega: `mvn -o -B -f backend/pom.xml verify` completado con **59 pruebas, 0 fallos, 0 errores** y generación de `backend/target/gestion-stock-0.1.0-SNAPSHOT.jar`. Los avisos de observadores fallidos en la salida de pruebas corresponden a fallas simuladas intencionalmente para comprobar su aislamiento.

La Etapa 1 termina aquí. La Etapa 2 necesita confirmación e incorporará `GestorInventario` Singleton clásico, los cuatro servicios, repositorios JPA y Adapter para ERP, proveedor de mensajería y Coto Digital. La Etapa 3 agregará REST/DTO y la Etapa 4 el frontend React con el Design System indicado.
