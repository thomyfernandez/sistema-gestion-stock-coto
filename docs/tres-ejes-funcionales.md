# Especificación funcional de los tres ejes: descuentos, combos y alertas

Documento técnico y funcional de referencia para la operación del inventario y canales comerciales de **COTO C.I.C.S.A.** Describe el funcionamiento integral, los casos de uso, las decisiones de diseño, los patrones implementados y los límites operativos en los tres ejes prioritarios del backoffice: valorización de precios y promociones, combos y existencias compuestas, y alertas automáticas de reposición.

---

## 1. Eje Descuentos y Valorización de Precios

### Caso de uso y necesidad de negocio
En el canal digital (Coto Digital) y en la venta por catálogo, los artículos y combos no se comercializan únicamente a su precio de lista base, sino que están sujetos a promociones comerciales dinámicas:
- Descuentos directos por porcentaje sobre el artículo.
- Promociones por volumen o beneficio tipo 2×1 (cada dos unidades, una bonificada).
- Descuentos condicionados al medio de pago (tarjetas bancarias, membresía Comunidad COTO, etc.).

El objetivo es permitir que un mismo producto tenga múltiples reglas promocionales acumulables, evaluadas en tiempo de consulta de precios sin alterar el precio de lista original persistido en la base de datos ni generar escrituras innecesarias en el inventario.

### Patrón de diseño: Decorator
El cálculo de precios se resuelve mediante el patrón **Decorator** en `ServicioValorizacion` y los componentes de valorización (`CalculadorPrecio`, `DescuentoPorcentual`, `DosPorUno`, `DescuentoMedioPago`):

```
                   ┌────────────────────┐
                   │  CalculadorPrecio  │ (Interfaz componente)
                   └─────────┬──────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
┌───────────────────────┐         ┌────────────────────────┐
│  PrecioBaseProducto   │         │   DecoradorPromocion   │ (Abstract Decorator)
└───────────────────────┘         └───────────┬────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
        ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
        │  DescuentoPorcentual  │ │       DosPorUno       │ │  DescuentoMedioPago   │
        └───────────────────────┘ └───────────────────────┘ └───────────────────────┘
```

1. **Componente base:** `PrecioBaseProducto` toma el precio unitario del producto y calcula el subtotal lineal multiplicándolo por la cantidad solicitada (`precioUnitario * cantidad`).
2. **Cadena de decoradores:** A partir de las promociones activas registradas para el producto (obtenidas desde la tabla `promocion` y ordenadas según el campo `orden`), `ServicioValorizacion` envuelve sucesivamente el calculador base con las instancias decoradoras correspondientes.
3. **Acumulación secuencial:** Cada decorador evalúa su condición (por ejemplo, si el medio de pago coincide o si la cantidad de unidades permite aplicar el 2×1), aplica su beneficio sobre el subtotal resultante del eslabón anterior y registra el identificador de la promoción aplicada.
4. **Redondeo final:** La cadena preserva precisión decimal en los cálculos intermedios y efectúa un único redondeo a dos decimales al final del proceso (`BigDecimal.setScale(2, RoundingMode.HALF_UP)`), evitando distorsiones por redondeos acumulados en cada etapa.

### Decisiones tomadas
- **Inmutabilidad del precio de lista:** La entidad `Producto` jamás muta su precio por efecto de una promoción. El precio final y el ahorro obtenido se calculan al vuelo mediante `ConsultaPrecios`.
- **Orden determinista:** La tabla `promocion` posee un índice único `(producto_id, orden)`. El orden numérico define con total precisión cuál regla se evalúa primero (por ejemplo, aplicar primero un 15% de descuento por categoría y luego un 10% adicional por pago con tarjeta de crédito bancaria).
- **Filtrado por medio de pago:** Si la solicitud al catálogo no especifica medio de pago o este no coincide con el definido en la promoción, el decorador `DescuentoMedioPago` delega la ejecución al siguiente eslabón sin alterar el importe.

### Límites del eje
- **Alcance por producto:** Las promociones aplican directamente al producto consultado (simple o combo). El sistema no calcula descuentos cruzados entre artículos distintos en un changuito multilínea (por ejemplo, "llevando producto A, 50% en producto B").
- **Evaluación en memoria:** Las promociones se aplican durante la consulta HTTP o el despacho digital; no se persisten tablas de precios precalculados, garantizando coherencia inmediata ante cualquier cambio o desactivación de una regla.

---

## 2. Eje Combos y Disponibilidad de Stock

### Caso de uso y necesidad de negocio
COTO ofrece combos comerciales que agrupan varios artículos (por ejemplo, "Canasta Familiar", "Kit Limpieza", "Set Asado"). Tradicionalmente, los combos se gestionaban únicamente de manera virtual (su disponibilidad derivaba de la existencia de sus partes sueltas). Sin embargo, en la operación diaria de sucursales se requiere:
1. Poder comercializar combos virtuales sin armar físicamente nada por adelantado.
2. Poder armar bultos físicos cerrados en depósito o salón (ej. 50 canastas ya encintadas y listas para llevar en góndola).
3. Asegurar que las reservas de Coto Digital y las compras en salón convivan sin generar inconsistencias ni sobreventas.

### Patrones de diseño: Composite y Strategy

#### 1. Composite (Estructura de artículos y combos)
El modelo de existencias implementa el patrón **Composite**:
- **Component (`Stock`):** Interfaz/clase abstracta común que expone `getDisponible()`, `getProducto()`, `aplicarMovimiento(...)` y contratos de reserva.
- **Leaf (`StockSimple`):** Representa artículos unitarios sueltos (arroz, aceite, leche). Mantiene contadores físicos: `stockFisico`, `stockExhibicion` y `stockReservado`. Su disponible se calcula como:
  $$\text{disponible} = \text{stockFisico} - \text{stockExhibicion} - \text{stockReservado}$$
- **Composite (`ComboStock`):** Contiene una lista de líneas (`LineaCombo`), donde cada una referencia a otro `Stock` (pudiendo ser simple o incluso otro combo anidado) junto con su `cantidadEnCombo`.

#### 2. Strategy (Criterio de reserva de combos)
Para resolver la reserva de un combo cuando este posee unidades físicas ya armadas y a la vez sus componentes admiten armar unidades adicionales virtuales, se implementó el patrón **Strategy** mediante `EstrategiaReservaCombo`:
- **`ReservaFisicoPrimero` (Estrategia por defecto):** Reserva prioritariamente las unidades ya armadas en stock propio del combo. Si la cantidad solicitada supera el stock físico propio disponible, la diferencia se reserva sobre los componentes virtuales en el árbol. Esto minimiza el impacto sobre los artículos sueltos de góndola.
- **`ReservaVirtualPrimero` (Estrategia configurable):** Reserva prioritariamente los componentes virtuales, preservando los combos físicos ya armados en el salón de ventas para los clientes presenciales de la sucursal.

### Manejo de stock propio vs disponible virtual
Un `ComboStock` mantiene sus propios contadores físicos (`stockFisico`, `stockExhibicion`, `stockReservado`) junto con su árbol de componentes:
- **`disponiblePropio`:** Calculado a partir de sus contadores propios:
  $$\text{disponiblePropio} = \max(0, \text{stockFisico} - \text{stockExhibicion} - \text{stockReservado})$$
- **`disponibleVirtual`:** Es el cuello de botella de sus componentes sueltos:
  $$\text{disponibleVirtual} = \min_{c \in \text{componentes}} \left\lfloor \frac{\text{disponible}(c)}{\text{cantidadEnCombo}(c)} \right\rfloor$$
- **`disponibleTotal`:** La disponibilidad visible y comercializable del combo es la suma directa de ambas dimensiones:
  $$\text{disponibleTotal} = \text{disponiblePropio} + \text{disponibleVirtual}$$

#### Movimientos de Armado y Desarmado
- **`ARMADO_COMBO`:** Se ejecuta una transacción atómica que descuenta $N \times \text{cantidadEnCombo}$ de cada componente (disminuyendo el físico de los artículos simples) e incrementa en $N$ el `stockFisico` propio del combo.
- **`DESARMADO_COMBO`:** Descuenta $N$ unidades del `stockFisico` propio del combo y restituye proporcionalmente las unidades físicas a cada componente.

### Respuestas clave del negocio

#### A. Componentes compartidos entre combos
*¿Qué sucede cuando dos combos distintos comparten un mismo artículo componente? (Por ejemplo: el "Combo Desayuno" lleva 1 Café y 2 Leches, y el "Combo Merienda" lleva 1 Galletitas y 1 Leche).*

1. **Cálculo de disponibilidad independiente:**
   La disponibilidad virtual de cada combo se calcula a partir del disponible instantáneo de la Leche. Si hay 10 leches disponibles en depósito:
   - El Combo Desayuno muestra hasta 5 unidades virtuales disponibles ($\lfloor 10 / 2 \rfloor$).
   - El Combo Merienda muestra hasta 10 unidades virtuales disponibles ($\lfloor 10 / 1 \rfloor$).
2. **Consumo y concurrencia:**
   La disponibilidad virtual es una proyección teórica; los componentes no se duplican. Cuando entra una reserva o venta efectiva por 4 Combos Desayuno:
   - Se comprometen 8 leches.
   - El disponible de la leche cae de 10 a 2 unidades.
   - Automáticamente, el disponible virtual del Combo Desayuno cae a $\lfloor 2 / 2 \rfloor = 1$, y el del Combo Merienda cae a $\lfloor 2 / 1 \rfloor = 2$.
3. **Garantía transaccional:**
   Para prevenir sobreventas cuando dos pedidos ingresan en simultáneo para combos que compiten por el mismo insumo, `GestorInventario` adquiere un bloqueo pesimista a nivel de fila (`SELECT ... FOR UPDATE` o `bloqueo_operacion`) durante toda la transacción. La primera reserva que comitea consume el stock; la segunda reevalúa las existencias reales y, si no alcanza, rechaza la operación con código `STOCK_INSUFICIENTE` (HTTP 409 Conflict) sin corromper los contadores.

#### B. Venta del artículo suelto que deja sin stock al combo
*¿Qué sucede si un cliente presencial o digital compra las últimas unidades sueltas de un artículo que forma parte de un combo?*

1. **Impacto en el disponible virtual:**
   Si la existencia suelta de Leche llega a cero, el cálculo $\min(\dots)$ de cualquier combo que la contenga resulta inmediatamente en $0$. El disponible virtual del combo se anula al instante sin necesidad de procesos batch ni recomputaciones periódicas.
2. **Protección del stock propio armado:**
   Si la sucursal previamente realizó el movimiento `ARMADO_COMBO` (por ejemplo, armó físicamente 15 canastas cerradas):
   - Esas 15 unidades ya migraron sus componentes al stock físico del combo en el momento del armado.
   - Aunque en góndola no quede ni un solo sachet de leche suelta, el combo **sigue teniendo 15 unidades disponibles para la venta** gracias a su `disponiblePropio`.
   - La venta suelta no puede canibalizar insumos que ya fueron encapsulados en un bulto cerrado.

### Límites del eje
- **Sin autorreferencias ni ciclos:** El esquema relacional y el dominio validan que un combo no pueda contenerse a sí mismo directa ni indirectamente (`ck_combo_no_autorreferencia`).
- **Nivel de anidamiento:** Aunque el patrón Composite admite recursividad arbitraria, por rendimiento y control operativo las fórmulas de combos suelen limitarse a un máximo de 2 niveles (combo compuesto por simples y combos secundarios).

---

## 3. Eje Alertas y Notificaciones de Reposición

### Caso de uso y necesidad de negocio
Evitar el quiebre de stock en sucursal requiere detectar proactivamente cuando un artículo cae por debajo de su margen de seguridad, alertando inmediatamente a los responsables del área (repositor, analista de compras o supervisor) a través del canal adecuado (Email, SMS o WhatsApp).

### Patrones de diseño: Observer y Strategy

```
               ┌─────────────────────┐
               │  GestorInventario   │ (Sujeto / Observable)
               └──────────┬──────────┘
                          │ notifica cambios de stock
                          ▼
               ┌─────────────────────┐
               │   AlertaStockBajo   │ (Observer)
               └──────────┬──────────┘
                          │ evalúa umbral y resuelve contacto
                          ▼
            ┌───────────────────────────┐
            │   ServicioNotificaciones  │ (Contexto Strategy)
            └─────────────┬─────────────┘
                          │ delega según canal resuelto
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│NotificadorMail│ │ NotificadorSMS│ │NotificadorWApp│ (Estrategias concretas)
└───────────────┘ └───────────────┘ └───────────────┘
```

1. **Observer (`AlertaStockBajo`):**
   - Escucha los eventos generados por `GestorInventario` tras la aplicación de cualquier movimiento físico, merma, despacho o reserva digital.
   - Compara el nuevo disponible contra el `umbralAlerta` estipulado en la `PoliticaReposicion` del artículo.
   - Si el disponible es inferior al umbral, dispara la generación de un episodio de alerta.
2. **Strategy (`ServicioNotificaciones` y `Canal`):**
   - El canal de comunicación no está acoplado al generador de la alerta.
   - La interfaz `Notificador` define el contrato de envío (`notificar(destino, mensaje)`).
   - Existen estrategias concretas intercambiables para cada canal: `NotificadorEmail`, `NotificadorSMS` y `NotificadorWhatsApp`, las cuales delegan en adaptadores específicos (`AdaptadorProveedor`, `AdaptadorProveedorSMS`, etc.).

### Resolución jerárquica de destinatarios
Para no obligar al operador a configurar un teléfono o correo en cada uno de los miles de artículos del catálogo, el sistema aplica una cascada de resolución en tres niveles:

1. **Destinatario particular del artículo:** Si la política de reposición del producto tiene asignado un `contacto_id` y `canal_alerta` específico, se utiliza este canal preferencial (ej. comprador especialista en lácteos).
2. **Destinatario de la categoría:** Si el artículo no posee asignación particular, el sistema busca si la `Categoria` a la que pertenece tiene configurado un contacto y canal (ej. equipo de compras de Frescos).
3. **Destinatario general de sucursal:** Si ni el artículo ni la categoría poseen asignación, se recurre a la configuración global en `configuracion_avisos` (teléfono o correo general de guardia de la sucursal).

### Garantías de entrega y patrón Outbox
- **Consistencia transaccional:** La notificación no se envía de forma sincrónica durante la transacción del inventario (lo que arriesgaría lentitud o fallas si el proveedor externo está caído).
- **Outbox en base de datos:** El evento se persiste en la tabla `evento_integracion` con estado `PENDIENTE` en la misma transacción que el movimiento de stock.
- **Inmutabilidad de alertas pendientes:** Los avisos ya encolados capturan el destinatario y mensaje exactos del momento en que ocurrió el quiebre. Editar un contacto o cambiar de categoría con posterioridad no altera los mensajes pendientes de entrega.
- **Reintentos automáticos:** Un despachador en segundo plano lee los eventos pendientes en orden FIFO y los procesa contra las estrategias de notificación, con registro de intentos y último error.

### Límites del eje
- **Canal validado:** No se permite asignar un canal a un contacto si este carece del dato correspondiente (por ejemplo, no se puede seleccionar WhatsApp para un contacto que sólo tiene cargado correo electrónico).
- **Control de spam/duplicados:** Las alertas se disparan en la transición de quiebre; movimientos subsiguientes mientras el producto continúe por debajo del umbral sin haber sido repuesto no generan alertas redundantes infinitas.

---

## 4. Matriz de Decisiones Arquitectónicas y Límites del Sistema

| Eje funcional | Patrón GoF principal | Patrón secundario | Decisión de diseño clave | Límite operativo intencional |
| :--- | :--- | :--- | :--- | :--- |
| **Descuentos y Valorización** | **Decorator** (`CalculadorPrecio`) | N/A | Cálculo en memoria sin alterar precio de lista en base de datos. Acumulación por orden determinista. | Descuentos aplicables únicamente por producto individual, sin reglas cruzadas multilínea. |
| **Combos y Existencias** | **Composite** (`ComboStock` / `StockSimple`) | **Strategy** (`EstrategiaReservaCombo`) | Convivencia de stock propio armado con disponible virtual. Bloqueo transaccional ante componentes compartidos. | Sin autorreferencias cíclicas en combos; estructura recomendada hasta 2 niveles de profundidad. |
| **Alertas de Reposición** | **Observer** (`AlertaStockBajo`) | **Strategy** (`Notificador`) | Desacople de detección y envío. Resolución jerárquica: Artículo → Categoría → Sucursal. Outbox transaccional. | Envío asincrónico desacoplado de la transacción principal; canales condicionados a los datos cargados en el contacto. |

