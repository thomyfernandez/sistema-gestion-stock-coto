# Temporadas de reposición estacional

La política de reposición `ESTACIONAL` ya no depende de un factor que Compras carga y quita a mano. El sistema sabe en qué época del año está: Compras define **temporadas** con fecha de inicio y de fin (día y mes, se repiten todos los años) y un factor, para **uno o varios artículos** o para toda una **categoría**. Fuera de toda temporada el factor es 1 y se repone el objetivo base.

```
objetivo del día = ceil(objetivo base × factor de la temporada vigente)
pedido           = max(0, objetivo del día − disponible − pedidos pendientes o enviados)
```

## Uso

1. **Compras → Temporadas**: lista las temporadas con su alcance, período, factor y si rigen hoy (lo informa el backend con la fecha de la sucursal).
2. **Nueva temporada**: nombre, alcance (**Artículos**: uno o más artículos simples elegidos de una lista con buscador; o **Una categoría**), desde y hasta (día y mes) y factor (mayor que 0 y hasta 10; 1,5 repone un 50 % más). Si el fin es anterior al inicio, la temporada **cruza el año**: del 15/11 al 06/01 va de mediados de noviembre a Reyes.
3. **Compras → Reposición → Configurar** en un artículo con política *Estacional*: el modal pide sólo el **objetivo base** y muestra qué temporada rige hoy, con qué factor y el objetivo resultante.

El cambio de temporada no dispara un pedido por sí solo: el factor nuevo se aplica en el próximo movimiento del artículo o en la próxima **Evaluar reposición**, como cualquier cambio de política.

## Qué factor rige

Para cada artículo con política `ESTACIONAL`, en la fecha de la sucursal:

1. La **temporada que incluye al artículo** y abarca esa fecha.
2. Si no hay, la **temporada de su categoría** que incluya esa fecha.
3. Si no hay ninguna, **factor 1**.

Es la misma jerarquía artículo → categoría → sucursal que usan los avisos. Dos temporadas que **comparten algún artículo**, o de la **misma** categoría, no pueden compartir días (la API responde `409 TEMPORADA_SUPERPUESTA` y nombra el artículo en común), así el factor vigente siempre es uno solo. Dos temporadas con las mismas fechas pero sin artículos en común no chocan. Una temporada de artículos sí puede solaparse con la de su categoría: gana la de artículos.

Los límites se incluyen. Un 29/02 es válido: una temporada que termina el 29/02 termina el 28/02 en los años no bisiestos, y una que empieza el 29/02 empieza el 01/03.

## Fecha de la sucursal

La estrategia nunca llama a `LocalDate.now()` sin reloj: recibe el `Clock` de la aplicación y calcula `LocalDate.now(reloj)` en cada evaluación. El reloj usa la zona de la sucursal (`coto.sucursal.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`), para que la temporada empiece a la medianoche local y no a las 21 h del día anterior (medianoche UTC). Los instantes que se guardan no cambian: siguen siendo UTC.

## Diseño

| Pieza | Capa | Rol |
| --- | --- | --- |
| `Temporada` | dominio | Record con `MonthDay` desde/hasta, factor y alcance: `stockIds` (lista sin duplicados, hasta 200) o `categoriaId`. `incluye(fecha)`, `superponeCon(otra)`, `comparteAlcance(otra)`, `articulosEnComun(otra)`. Valida factor (0 < f ≤ 10, 4 decimales) y alcance único. |
| `CalendarioTemporadas` | dominio | Temporadas de un artículo y su categoría; `factorPara(fecha)` devuelve factor, temporada y origen (`ARTICULO`, `CATEGORIA`, `SIN_TEMPORADA`). |
| `ReposicionEstacional` | dominio (Strategy) | `ConcreteStrategy` que recibe objetivo base, calendario y `Clock`. Conserva el constructor `(objetivoBase, factor)` como factor fijo todo el año. |
| `PoliticaReposicion.crearEstrategia(calendario, reloj)` | dominio | Ya no guarda factor. Para `ESTACIONAL` valida que `objetivo base × 10` entre en un entero, así ninguna temporada puede desbordar el cálculo. |
| `RepositorioTemporadas` / `RepositorioTemporadasJpa` | puerto / adaptador | Persistencia en `temporada_reposicion` y `temporada_articulo` (`@ElementCollection`). |
| `ServicioReposicion.estrategiaPara(politica, stock, temporadas)` | servicio | Arma la estrategia con las temporadas del artículo y su categoría. El coordinador de eventos lee las temporadas una sola vez por evaluación. |
| `ServicioTemporadas` | servicio | Alta, edición y baja bajo el bloqueo de inventario; valida alcance (artículos simples o categoría existentes) y superposición. |
| `TemporadaController` | API | `@Tag "Temporadas de reposición"`. |

El contrato `EstrategiaReposicion.calcularCantidad(Stock)` no cambió: la fecha llega por el reloj de la estrategia concreta, no por la interfaz.

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/api/temporadas` | Todas, con `vigente` y `cruzaElAnio` calculados por el servidor |
| POST | `/api/temporadas` | Alta; `201` con `Location` |
| PUT | `/api/temporadas/{id}` | Reemplazo; `404` si no existe |
| DELETE | `/api/temporadas/{id}` | Baja; `204` |
| GET | `/api/productos/{id}/factor-estacional` | `{ stockId, fecha, factor, origen, temporadaId, temporada }` del día |

```json
{ "nombre": "Navidad y Reyes", "stockIds": [5, 7], "categoriaId": null,
  "desdeDia": 15, "desdeMes": 11, "hastaDia": 6, "hastaMes": 1, "factor": 1.5 }
```

Escritura: `COMPRAS` y `SUPERVISOR` (`REPOSITOR` y `RECEPCION` reciben 403). Errores en `application/problem+json`: fecha inexistente (31/04), factor fuera de rango, mes o día fuera de rango, artículos y categoría a la vez o ninguno de los dos, más de 200 artículos, o un combo entre los artículos → `400`; artículo o categoría inexistente → `404`; el factor vigente de un combo → `400` (se repone por sus componentes); superposición → `409 TEMPORADA_SUPERPUESTA`, con la temporada que choca en el `detail`.

La política (`/api/productos/{id}/politica-reposicion` y `/api/politicas`) ya no devuelve `factorEstacional`. Un cliente anterior que lo siga enviando en el PUT no falla: el campo se ignora.

## Persistencia y migración

Tabla `temporada_reposicion (id, nombre, categoria_id, desde_mes, desde_dia, hasta_mes, hasta_dia, factor)` con `CHECK` de rangos de mes y día y `0 < factor ≤ 10`; `categoria_id` nulo significa que la temporada es de artículos. Tabla puente `temporada_articulo (temporada_id, stock_id, tipo_stock)` con `PRIMARY KEY (temporada_id, stock_id)`, `ON DELETE CASCADE` y la misma FK `(stock_id, tipo_stock) → stock(id, tipo)` que la política, que impide temporadas sobre combos. Que haya al menos un artículo o una categoría, y no ambos, lo garantiza el dominio. Se elimina `politica_reposicion.factor_estacional`.

`db/upgrade-temporadas.sql` (después de `upgrade-reservas-combo.sql`, idempotente) crea las tablas y convierte cada política `ESTACIONAL` con factor distinto de 1 en una temporada del artículo **de todo el año** (01/01 al 31/12) con ese factor, llamada "Factor anterior (todo el año)". Así ningún pedido cambia al migrar; después Compras ajusta las fechas reales. Los factores de políticas de otro tipo nunca se usaban y se descartan. Un factor mayor a 10 se limita a 10. Como la política estacional exige que el objetivo base por 10 entre en un entero, un objetivo estacional mayor a 214.748.364 se limita a ese valor.

Los datos de demostración traen el pan dulce (id 5, objetivo base 40) con la temporada **Navidad y Reyes**, del 15/11 al 06/01, ×1,5.

## Verificación

- Dominio (`TemporadasTest`): varios artículos (normalización, artículos en común, tope de 200), dentro y fuera de temporada, cruce de año (20/12 y 03/01 adentro; 10/01 y 14/11 afuera), 29/02, superposición, prioridad artículo → categoría → 1 y cambio de día según la zona del reloj.
- `ServiciosTest`: el contexto de Strategy arma la estrategia con las temporadas del artículo y de su categoría.
- API (`TemporadasApiTest`, con un reloj que la prueba mueve de fecha): temporada de varios artículos (choque por artículo en común, quitar un artículo), evaluación con y sin temporada, origen del factor, alta/edición/baja, 409 y validaciones; roles en `AutenticacionYRolesTest`.
- Migración (`UpgradeTemporadasTest` en H2, `PostgresRealTest` en PostgreSQL 17): dos ejecuciones seguidas, temporada migrada del pan dulce y columna eliminada.
- Frontend: Vitest (`Seasons.test.tsx`, `PolicyForm.test.tsx`) y recorrido Playwright de alta, superposición, baja y tarjeta del factor.
