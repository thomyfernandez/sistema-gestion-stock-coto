# Diseño — Temporadas de reposición estacional

Dos piezas: **A.** gestión de temporadas (lista, alta/edición en modal, baja con confirmación) y **B.** tarjeta de factor vigente en `PolicyForm` cuando la política es `ESTACIONAL`. Contrato de API: punto 4 de `plan.md`.

## 0. Ubicación: pestaña "Temporadas" en Compras

- Ruta `#/compras/temporadas`. En `Purchases.tsx`, `tabs` queda: **Reposición · Temporadas · Productos y combos · Promociones · Actividad** (al lado de Reposición, que es lo que modifica).
- En `App.tsx`, `subtitles` suma `temporadas: 'Temporadas'` para el breadcrumb.
- **Por qué no en Configuración**: esa vista es sólo `SUPERVISOR` y el pedido exige que **Compras** cree, edite y borre temporadas (CA6). **Por qué no en Descuentos**: es precio de venta, no reposición. Compras ya es `COMPRAS` + `SUPERVISOR`, los mismos roles con permiso de escritura, así que no hace falta un modo de sólo lectura. Si igual querés el permiso explícito en la UI, `PERMISOS.editarTemporadas = ['COMPRAS', 'SUPERVISOR']`, como `editarCategoria`.
- `PageHeading` no lleva acciones en esta pestaña (`actions` sigue siendo sólo para `catalogo`). La acción primaria vive en el encabezado del panel.

## A. Gestión de temporadas

### A.1 Flujo

```
Compras → Temporadas
 ├─ Cargando → lista | error (Reintentar) | vacío
 ├─ [Nueva temporada] → Modal alta
 │    ├─ Crear temporada → 201 → cierra, toast, lista recargada, foco vuelve al botón
 │    ├─ 409 superposición / 400 desborde → error en el modal, datos intactos, el operador corrige
 │    ├─ 400 / 403 / red → error en el modal, datos intactos
 │    └─ Cancelar / Esc / X / clic fuera → cierra sin guardar (bloqueado mientras `busy`)
 ├─ [Editar] (fila) → Modal edición (mismos campos precargados)
 │    └─ Guardar cambios → 200 → igual que el alta | 404 → "ya no existe", Actualizar lista
 └─ [Eliminar] (fila) → Confirmation danger
      ├─ Eliminar temporada → 204 → cierra, toast, foco a "Nueva temporada"
      ├─ error → mensaje en el `inline-error` de la confirmación
      └─ Volver → cierra sin cambios
```

Datos: `useResource('temporadas', signal => api.temporadas(signal), revision)`. Altas, ediciones y bajas con `run(accion, mensaje, ['politicas'])`; `revision` siempre avanza, así que la lista se vuelve a pedir.

### A.2 Estructura de pantalla

Layout: `div.settings-layout` (ya existe: 1,6 fr / 1 fr, una columna a ≤ 1200 px).

| Zona | Contenido | Tipografía |
| --- | --- | --- |
| Principal (`section.panel`) | Encabezado + barra + tabla de temporadas | `h2` Heading 4 (Body large a ≤ 1080, regla existente); bajada `p` en Caption |
| Secundaria (`aside.panel.settings-help`) | "Cómo se elige el factor" | `svg` 32 px, `h2` Heading 4, `p` Body |

A 1024 px de ventana el área de contenido mide ~782 px (menú lateral de 194 px), menos que las zonas 645 + 315 del DS. Por eso, igual que en Alertas y avisos, la ayuda baja **debajo** de la tabla. Desde 1201 px se ven lado a lado.

#### Panel principal

```
┌ panel ───────────────────────────────────────────────────────────┐
│ Temporadas de reposición                      [+ Nueva temporada] │  panel-heading
│ Ajustan el objetivo de la política estacional en fechas que se     │
│ repiten cada año.                                                  │
│ 3 temporadas · 1 vigente hoy                                       │  table-toolbar > span.caption
├──────────────────────────────────────────────────────────────────┤
│ Temporada          │ Período          │ Factor │ Estado    │      │  thead
│ Navidad y Reyes    │ 15/11 al 06/01   │  ×1,5  │ ● Vigente │ ✎ 🗑 │
│ Artículo: Pan dulce│ Cruza el año     │ 50 % más│  hoy     │      │
└──────────────────────────────────────────────────────────────────┘
```

- `panel-heading`: `h2` "Temporadas de reposición"; `p` "Ajustan el objetivo de la política estacional en fechas que se repiten cada año."; a la derecha `Button variant="primary" icon={Plus}` "Nueva temporada" (tamaño md, 44 px). Deshabilitado mientras `loading` sin datos, con `error` o con `busy`, igual que "Nueva promoción". Necesita un `ref` para devolverle el foco después de una baja.
- `table-toolbar`: `span.caption` con `aria-live="polite"`: "{n} temporadas · {m} vigentes hoy" (singular: "1 temporada", "1 vigente hoy"). Mientras recarga con datos visibles: "Actualizando…".
- `div.table-scroll` `role="region"` `aria-label="Temporadas de reposición"` `tabIndex={0}` `aria-busy={loading}` → `table`:

| Columna | `th` | Celda |
| --- | --- | --- |
| Temporada | `scope="col"` | `div.stacked-cell`: `strong` nombre; `span.caption` alcance: **"Artículo: {nombre}"** o **"Categoría: {nombre} · {k} artículos"** (k = simples de la categoría). Si el artículo o la categoría ya no existe: "Artículo #0005" o "Categoría sin nombre". |
| Período | `scope="col"` | `div.stacked-cell`: `span.nowrap` "{dd/mm} al {dd/mm}" (por ej. "15/11 al 06/01"); `span.caption`: "Cruza el año" si desde > hasta, "Todo el año" si es 01/01 al 31/12, "Un solo día" si desde = hasta; si no, nada. |
| Factor | `scope="col" className="numeric"` | `div.stacked-cell` alineado a la derecha: `strong` "×1,5" (es-AR, hasta 4 decimales sin ceros de más); `span.caption` "50 % más" / "20 % menos" / "Sin ajuste" (f = 1). Redondear el % a 2 decimales como máximo. |
| Estado | `scope="col"` | `Badge tone="success"` "Vigente hoy" o `Badge tone="neutral"` "Fuera de temporada" (el texto lleva el significado, no sólo el color). Debajo, sólo si ningún artículo alcanzado tiene política `ESTACIONAL`: `span.caption.block` "Sin efecto: ningún artículo con política estacional". |
| Acciones | `<span className="sr-only">Acciones</span>` | `Button variant="ghost" icon={Edit3}` "Editar", `aria-label="Editar temporada {nombre}"`; `Button variant="ghost" className="icon-button" icon={Trash2}`, `aria-label="Eliminar temporada {nombre}"` (42 × 42 px). Las dos `disabled={busy}`. |

- **Orden de filas** (sólo presentación): vigentes primero, después por mes y día de inicio, después por nombre.
- Factor y período no se tiñen: ni el rojo ni los tonos semánticos se usan para decorar.

#### Ayuda secundaria (`aside.panel.settings-help`)

- `CalendarRange` 32 px `aria-hidden`.
- `h2` "Cómo se elige el factor".
- `p` "Cada día, la política estacional usa la temporada vigente del artículo. Si no tiene, usa la de su categoría. Si no rige ninguna, el factor es 1 y se repone el objetivo base."
- `p` "Las fechas se repiten todos los años. Si el fin es anterior al inicio, la temporada cruza el año: del 15/11 al 06/01 va de noviembre a enero."
- `p` "Las temporadas de un mismo artículo o de una misma categoría no pueden superponerse."
- `p` "El objetivo del día es el objetivo base por el factor, redondeado hacia arriba. Sólo cambia en los artículos con política Estacional."
- `a.text-link` "Configurar políticas" → `#/compras/reposicion`.

### A.3 Modal de alta y edición (`SeasonForm`)

`Modal` normal (540 px, no `wide`). Título "Nueva temporada" o "Editar temporada". Descripción: "Ajustá el objetivo de reposición en un período que se repite cada año."

`form` → `div.modal-body.form-stack` (24 px entre bloques):

1. **Nombre**: `Field label="Nombre de la temporada" hint="Por ejemplo: Navidad y Reyes, Día del Niño."` → `input required maxLength={80}`.
2. **Alcance**: `fieldset` → `legend` "Se aplica a" → `div.channel-options.two` (clase nueva) con dos `label.channel-choice` (`.selected` si está marcada):
   - `input type="radio" name="alcance" value="ARTICULO"` + `Package` 24 `aria-hidden` + "Un artículo".
   - `input type="radio" name="alcance" value="CATEGORIA"` + `FolderOpen` 24 `aria-hidden` + "Una categoría". `disabled` si no hay categorías.
3. **Destino del alcance** (uno solo, según el radio; `key` distinto para que se reinicie):
   - Artículo: `Field label="Artículo" hint="Prevalece sobre la temporada de su categoría. Sólo artículos simples: los combos se reponen por sus componentes."` → `select required`, primera opción `value=""` "Seleccioná un artículo", después los `tipo === 'SIMPLE'` por nombre.
   - Categoría: `Field label="Categoría" hint="Rige en los artículos de la categoría que no tengan una temporada propia vigente."` → `select required`, primera opción "Seleccioná una categoría".
   - Aviso de efecto (debajo del select, `div.message.warning` sin `role`, ícono `AlertTriangle` 24 `aria-hidden`). Se calcula con `data.politicas`:
     - artículo sin política `ESTACIONAL`: "**{Artículo}** tiene política {Punto de pedido | Por demanda | sin configurar}: esta temporada no cambia sus pedidos hasta que la pases a Estacional."
     - categoría sin ningún artículo estacional: "Ningún artículo de {Categoría} tiene política Estacional: esta temporada no cambia sus pedidos por ahora."
     - categoría con algunos: no hay aviso; agregar al hint: " · {k} de {n} artículos con política Estacional."
4. **Período**: `div.period-range` (clase nueva, 16 px entre filas):
   - `fieldset` → `legend` "Desde" → `div.period-fields` (clase nueva: día 1 fr, mes 2 fr) → `Field label="Día"` `select required` (opción vacía "Día", 1 a N) + `Field label="Mes"` `select required` (opción vacía "Mes", "Enero"… "Diciembre", `value` 1–12).
   - `fieldset` → `legend` "Hasta" → igual.
   - N = días del mes (febrero = 29). Si el mes cambia y el día queda fuera, se ajusta al último del mes. No se usa `input type="date"` porque obliga a elegir un año.
   - Resumen `p.form-info` `aria-live="polite"` (sólo con los cuatro valores cargados):
     - normal: "Del 1 de diciembre al 31 de diciembre de cada año."
     - cruza el año: "Del 15 de noviembre al 6 de enero del año siguiente. Cruza el fin de año."
     - mismo día: "Sólo el 25 de diciembre de cada año."
     - todo el año: "Todo el año: del 1 de enero al 31 de diciembre."
     - fin 29/02, agregar: " En los años no bisiestos termina el 28 de febrero."
     - inicio 29/02, agregar: " En los años no bisiestos empieza el 1 de marzo." (**confirmar con el backend**: con `MonthDay` el 28/02 queda fuera y el 01/03 adentro).
     - incompleto: no se muestra.
5. **Factor**: `Field label="Factor de temporada"` → `input required type="number" inputMode="decimal" min="0.0001" max="10" step="0.0001" placeholder="1,5"`. El `hint` cambia según el valor:
   - vacío o inválido: "Mayor que 0 y hasta 10. Por ejemplo, 1,5 repone un 50 % más que el objetivo base."
   - > 1: "Repone un {p} % más que el objetivo base."
   - = 1: "Sin ajuste: repone el objetivo base."
   - < 1: "Repone un {p} % menos que el objetivo base."
6. `ErrorMessage error={error}`.

`footer.modal-footer`: `Button` (secondary) "Cancelar" `disabled={busy}` + `Button variant="primary" type="submit" loading={busy}` **"Crear temporada"** (alta) o **"Guardar cambios"** (edición).

Valores iniciales del alta: nombre vacío, alcance `ARTICULO`, sin artículo, día y mes vacíos, factor vacío. En la edición se precargan todos los campos y el alcance se puede cambiar.

Orden de tabulación: Cerrar (X) → Nombre → radios de alcance (flechas entre opciones) → Artículo/Categoría → Desde día → Desde mes → Hasta día → Hasta mes → Factor → Cancelar → Crear/Guardar. Coincide con el orden visual.

### A.4 Baja

`Confirmation` existente, `danger`:

- `title`: "Eliminar temporada {nombre}"
- `text`: "{Pan dulce | Los artículos de Panadería} dejan de ajustar su objetivo del {15/11} al {06/01}. Los pedidos ya generados no cambian."
- `children`, sólo si está vigente: `div.message.warning` con `AlertTriangle` 24: "Está vigente hoy. Desde la próxima evaluación se repone con {la temporada de la categoría, si hay una vigente | el objetivo base}." Si no se sabe si la categoría tiene una vigente, usá el texto general: "Está vigente hoy. Desde la próxima evaluación deja de aplicarse su factor."
- `label`: "Eliminar temporada". El botón secundario ya dice "Volver".
- Si sale bien, el foco va a "Nueva temporada", porque la fila que abrió la confirmación ya no existe.

### A.5 Estados

| Estado | Qué se ve |
| --- | --- |
| Cargando (sin datos) | Encabezado del panel visible; en lugar de la tabla, `p.muted.loading-label` "Cargando temporadas…"; "Nueva temporada" deshabilitado. |
| Recargando (con datos) | Tabla visible con `aria-busy`, la barra dice "Actualizando…". |
| Error de API (sin datos) | `ErrorMessage` dentro de `panel-body` + `Button icon={RefreshCw}` (secondary) "Reintentar" (vuelve a pedir con un contador local como `version`). Si el error no trae texto: "No pudimos cargar las temporadas. Revisá la conexión y reintentá." |
| Datos desactualizados (falla una recarga con datos previos) | `ErrorMessage` arriba de la tabla con el motivo; la tabla queda visible; la barra dice "Puede estar desactualizada · Reintentá." y se muestra "Reintentar". |
| Vacío | `Empty title="Todavía no hay temporadas" text="Cargá una temporada para que la política estacional ajuste el objetivo en fechas como Navidad o el Día del Niño. Fuera de toda temporada, el factor es 1."` sin `action` (la primaria ya está en el encabezado: una sola por zona). |
| Sin catálogo (ni simples ni categorías) | `Empty title="El catálogo está vacío" text="Creá un artículo o una categoría antes de cargar temporadas."`; "Nueva temporada" deshabilitado. |
| Sin conexión | El banner global `connection-error` ya lo cubre. Al guardar sin red, el modal queda abierto con los datos y muestra el error de red de `api.ts`. |
| Éxito | Toast del store (abajo). |

### A.6 Textos de resultado y errores

Toasts (`run`):

- Alta: "Temporada creada. Se aplica desde la próxima evaluación de reposición."
- Edición: "Temporada actualizada. Se aplica desde la próxima evaluación de reposición."
- Baja: "Temporada eliminada. Fuera de otras temporadas, el factor vuelve a 1."

Errores: la UI muestra el `detail` del problem+json. **Pedido al backend**: que el `detail` diga qué choca y qué hacer.

| Caso | HTTP | `detail` |
| --- | --- | --- |
| Superposición | 409 | "Se superpone con «Navidad y Reyes» (15/11 al 06/01) de Pan dulce. Cambiá las fechas o editá esa temporada." (en categoría: "…de la categoría Panadería…") |
| Desborde del objetivo | — | No ocurre al guardar temporadas: el factor tiene tope 10 y la política estacional exige objetivo base × 10 dentro de un entero (400 `CANTIDAD_FUERA_RANGO` al guardar la política). |
| Alcance inválido | 400 / 404 | 400 si es un combo o vienen ambos/ninguno; 404 si el artículo o la categoría no existen. |
| Fecha inexistente | 400 | "El 31/04 no existe. Elegí un día válido para ese mes." (con los selects no debería pasar) |
| Factor fuera de rango | 400 | "El factor tiene que ser mayor que 0 y hasta 10." |
| Sin permiso | 403 | "Tu usuario no puede modificar temporadas. Pedíselo a Compras o a un supervisor." |
| No existe (editar/eliminar) | 404 | "La temporada ya no existe. Actualizá la lista." |

## B. Política `ESTACIONAL`: tarjeta del factor vigente

### B.1 Cambios en `PolicyForm`

- `descriptions.ESTACIONAL`: "El objetivo base se ajusta con el factor de la temporada que rige cada día."
- Se quita `Field "Factor de temporada"` y el `div.form-grid`: queda **un solo** `Field label="Objetivo base" hint="Unidades a reponer fuera de temporada."` a todo el ancho.
- Debajo del objetivo base va la **tarjeta de factor vigente**. Reutiliza `.notice-resolution-card` y `.notice-resolution-content`; se agregan el desglose y las acciones (ver "CSS agregado").
- La consulta se hace sólo con `tipo === 'ESTACIONAL'`: `useResource(\`factor:${product.id}\`, signal => api.factorEstacional(product.id, signal), revision + intento)`.
- En la tarjeta de avisos que ya existe (misma pantalla) hay que sacar `style={{ color: 'var(--coto-red)' }}`, porque ese token no existe, y pasar el ícono `Mail` de 20 a 24 px. El color ahora sale de `.notice-resolution-card > svg`.

### B.2 Anatomía y estados

`div.notice-resolution-card` `role="region"` `aria-label="Factor de temporada vigente"` `aria-live="polite"` `aria-busy={loading}`:

**Cargando**

```
[LoaderCircle 24 .spin]  Consultando la temporada de hoy…
```

**Con temporada (origen ARTICULO o CATEGORIA)**

```
[CalendarRange 24]  Hoy rige «Navidad y Reyes» · ×1,5
                    Temporada del artículo, del 15/11 al 06/01.     ← span.caption
                    ─────────────────────────────────────────
                    Objetivo de hoy                 150 unidades    ← .notice-resolution-breakdown
                    100 × 1,5, redondeado hacia arriba · al 26/11/2026   ← span.caption
                    Administrar temporadas                          ← a.text-link
```

- `p`: "Hoy rige «{nombre}» · ×{factor}".
- `span.caption`: ARTICULO → "Temporada del artículo." · CATEGORIA → "Temporada de la categoría {nombre de la categoría}." Si la respuesta no trae fechas, se omite "del … al …". Si se agregan al contrato, conviene sumarlas.
- `div.notice-resolution-breakdown`: `span` "Objetivo de hoy" + `strong` "{n} unidades".
- `span.caption`: "{base} × {factor}, redondeado hacia arriba · al {fecha dd/mm/aaaa}".

**Sin temporada (SIN_TEMPORADA)**

- `p`: "Hoy no rige ninguna temporada · ×1".
- `span.caption`: "Se repone el objetivo base."
- Desglose: "Objetivo de hoy" / "{base} unidades"; `span.caption` "al {fecha}".

**Objetivo base vacío o inválido**: en lugar del `strong` del desglose va `span.caption` "Ingresá el objetivo base para ver el objetivo de hoy."

**Error de consulta** (`.notice-resolution-card.warning`, ícono `AlertCircle` 24):

- `p`: "No pudimos consultar la temporada de hoy."
- `span.caption`: "Podés guardar la política igual: el factor se calcula en cada evaluación de reposición."
- `div.notice-resolution-actions`: `Button size="sm" icon={RefreshCw}` (secondary, 40 px) "Reintentar" + `a.text-link` "Administrar temporadas".
- **No** bloquea "Guardar política".

**Enlace**: `a.text-link href="#/compras/temporadas" onClick={onClose}`, texto "Administrar temporadas". Si el formulario tiene cambios sin guardar (`values` ≠ inicial), sumar `span.caption` arriba del enlace: "Guardá la política antes de salir: los cambios sin guardar se pierden."

### B.3 Cálculo del objetivo de hoy (sólo vista previa)

`objetivo = ceil(base × factor)`, con el factor que devuelve el backend. **No usar `Math.ceil(base * factor)`**: `100 * 1.1` da `110.00000000000001` y redondearía a 111. Usar enteros (el factor tiene 4 decimales):

```ts
const objetivoHoy = (base: number, factor: number) => Math.ceil(base * Math.round(factor * 10000) / 10000);
```

Es seguro: base ≤ 2³¹ y factor ≤ 10, así que el producto cabe en 2⁵³. Es una vista previa: el valor real lo calcula el backend al evaluar.

### B.4 Error al guardar la política

Si el backend rechaza el objetivo base por desborde con el factor máximo de una temporada (400 `CANTIDAD_FUERA_RANGO`), el `detail` sugerido es: "Con ×{factor máximo} de «{temporada}», el objetivo base supera el máximo permitido. Bajá el objetivo base o el factor de esa temporada." Se muestra en el `ErrorMessage` del modal, como hoy.

## Accesibilidad

- Contraste: todo sale de tokens con AA comprobado (texto Neutral 900/700 sobre blanco y Neutral 50; Warning 800 sobre Warning 100; los badges existentes). No se agrega ningún color.
- El estado de una temporada tiene **texto** en el badge y el factor tiene **texto** ("50 % más"): nada depende sólo del color.
- Foco visible global (`:focus-visible` con Info 500). Al cerrar un modal vuelve al disparador (lo hace `Modal`); después de una baja va a "Nueva temporada".
- Objetivos táctiles: botones md 44 px, icon-button 42 px, `size="sm"` 40 px, selects e inputs de 46 px, tarjetas de alcance de ≥ 54 px.
- `aria-label` únicos en los botones de fila, con el nombre de la temporada. Los radios van dentro de `fieldset` + `legend`. Día y mes se agrupan con `legend` "Desde"/"Hasta", así que el lector anuncia "Desde, Día".
- `aria-live="polite"` en el resumen del período, en el contador de la barra y en la tarjeta del factor. Los errores entran por `ErrorMessage` (`role="alert"`).
- Íconos: `Plus`, `Edit3`, `Trash2`, `RefreshCw`, `Package`, `FolderOpen`, `AlertTriangle`, `AlertCircle`, `LoaderCircle` y `CalendarRange` a 24 px (32 px en la ayuda), todos `aria-hidden`.

## Responsive

| | 1024 px | 390 px |
| --- | --- | --- |
| Layout | Tabla a todo el ancho; la ayuda abajo (`settings-layout` en una columna a ≤ 1200) | Igual, márgenes de 16 px |
| Tabla | 5 columnas, sin desplazamiento (≥ 680 px disponibles) | `table-scroll` horizontal, como el resto de las tablas; la primera columna (nombre + alcance) se ve completa |
| Encabezado del panel | Título a la izquierda, botón a la derecha | Se envuelve (`.panel-heading:has(.button)`); el botón baja debajo del título |
| Modal | 540 px; alcance en dos tarjetas lado a lado; día/mes en una fila (1 fr / 2 fr) | `100vw − 24`; tarjetas de alcance apiladas (`.channel-options.two` a ≤ 540); día/mes siguen en una fila (~106 / ~212 px, entra "Septiembre"); los botones del pie ocupan mitad y mitad (regla existente) |
| Tarjeta de factor | Desglose en una línea | Desglose en una línea (`nowrap` en el valor); el `caption` se envuelve |

## CSS agregado (ya está en el repo)

`frontend/src/styles/app.css` (al final):

```css
.channel-options.two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.period-range { display: flex; flex-direction: column; gap: var(--space-2); }
.period-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: var(--space-2); }
@media (max-width: 540px) { .channel-options.two { grid-template-columns: minmax(0, 1fr); } }
```

`frontend/src/styles/components.css` (al final, refina la tarjeta de resolución):

```css
.notice-resolution-card > svg { flex: none; color: var(--neutral-700); }
.notice-resolution-card.warning > svg { color: var(--warning-800); }
.notice-resolution-breakdown { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-2); margin-top: var(--space-1); padding-top: var(--space-1); border-top: 1px solid var(--neutral-200); }
.notice-resolution-breakdown strong { font-size: var(--body-large); font-variant-numeric: tabular-nums; white-space: nowrap; }
.notice-resolution-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin-top: var(--space-1); }
```

No hay tokens nuevos. `docs/design-system/README.md` documenta la tarjeta en su variante de factor, el selector de día y mes, y las opciones en tarjetas.

Ojo: `.notice-resolution-content p` fuerza Body y Neutral 900. Por eso las aclaraciones de la tarjeta van en `<span className="caption">`, no en `<p className="caption">`.

## Helpers de formato sugeridos (`format.ts`, sin lógica de negocio)

- `diaMes(dia, mes)` → "15/11".
- `periodoCorto(t)` → "15/11 al 06/01"; `periodoLargo(t)` → "del 15 de noviembre al 6 de enero del año siguiente".
- `factor(f)` → "×1,5" (`Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 })`).
- `ajuste(f)` → "50 % más" / "20 % menos" / "Sin ajuste".
- `meses` = `['Enero', …, 'Diciembre']` y `diasDelMes(mes)` (febrero = 29).

## Auditoría

Revisé `git diff -- frontend/` y los archivos nuevos `Seasons.tsx` y `SeasonFactorCard` contra esta especificación y el DS. También lo verifiqué en el navegador con instancias aisladas (backend 18080, Vite 5174; Playwright a 1024 × 900 y 390 × 844).

**Diferencias aceptadas:** la superposición responde 409 con el `detail` "Se superpone con «X» (dd/mm al dd/mm) del mismo artículo|categoría…" (sin nombre del artículo). El modal lo muestra tal cual y queda abierto con los datos, así que el flujo A.1 no cambia; en A.6 hay que leer 409 donde dice 422. La tarjeta del factor no muestra "del … al …" porque el endpoint no devuelve fechas, como ya preveía B.2.

**Cumple:** pestaña y ruta (`Purchases.tsx:15`, breadcrumb "Temporadas"), una sola primaria por zona, `Empty` sin `action`, badges con texto, factor sin color, `aria-label` únicos por fila, `fieldset`/`legend` en alcance y período, ajuste del día al cambiar el mes, hints del factor, textos de los toasts, `Confirmation` con "deja"/"dejan" según el alcance (mejor que la spec), tarjeta con `Mail` 24 sin `style` inline de color (ya no usa `--coto-red`), y CSS nuevo solo con tokens (`.row-actions`, `.period-*`, `.channel-options.two`, `.notice-resolution-*`). A 390 px el modal se ve bien: alcance apilado y día/mes en una fila.

### Bloqueantes

1. **A 1024 px la tabla se desplaza y las acciones quedan fuera de la vista.** `frontend/src/pages/Seasons.tsx:61-70` junto con `frontend/src/styles/app.css:209` (`.stacked-cell { min-width: 160px }`). Lo medí: tabla de 1038 px en un área de 880 px con el menú contraído; con el menú expandido el área es de 720 px. Cuatro columnas `stacked-cell` de 208 px empujan "Editar" y "Eliminar" fuera de la vista: en la captura solo asoma el lápiz. Contradice "5 columnas, sin desplazamiento" (Responsive).
   **Corrección:**
   - Agregar `className="seasons-table"` al `div.table-scroll`.
   - En `app.css`: `.seasons-table td:not(:first-child) .stacked-cell { min-width: 0; }`.
   - Pasar "Editar" a solo ícono, como en Promociones (`Purchases.tsx:52`): `` <Button variant="ghost" className="icon-button" icon={Edit3} aria-label={`Editar temporada ${t.nombre}`} … /> ``. **Cambia A.2:** "Editar" va sin texto visible.
   - Resultado medido con las dos correcciones: 880/880 con el menú contraído, 730/720 con el menú expandido. Si molesta el desborde de 10 px, `.seasons-table :is(th, td):last-child { padding-inline: var(--space-2); }`.
2. **Después de eliminar, el foco se pierde (queda en `BODY`).** `frontend/src/pages/Seasons.tsx:46-50`. Verificado en el navegador: `document.activeElement` es `BODY`. Hay dos causas:
   - `focus()` se llama dentro de `onConfirm`, cuando el `<dialog>` de la confirmación sigue abierto y el resto de la página está inerte.
   - Después, al cerrarse, `Modal` le devuelve el foco al tacho de la fila. Esa fila desaparece cuando llega la lista nueva.

   Contradice A.4 y WCAG 2.4.3.
   **Corrección:** mover el foco después del desmontaje del modal:
   ```tsx
   const focoNueva = useRef(false);
   useEffect(() => { if (!deleting && focoNueva.current) { focoNueva.current = false; nueva.current?.querySelector('button')?.focus(); } }, [deleting]);
   async function eliminar(t: Temporada) { await run(…); focoNueva.current = true; }
   ```
   El efecto del padre corre después del cleanup del `Modal` hijo, así que gana sobre la restauración al disparador.
3. **En el alta aparece una caja informativa vacía.** `frontend/src/pages/Seasons.tsx:161`. El `p.form-info` se renderiza siempre: con el período incompleto es una barra Info 100 de 32 px sin texto, visible apenas se abre "Nueva temporada" (comprobado a 1024 y a 390). La spec dice "incompleto: no se muestra".
   **Corrección:** dejar el contenedor vivo y estilar solo con contenido: `<div aria-live="polite">{periodoCompleto && <p className="form-info">{periodoLargo(periodo)}</p>}</div>`. No usar `.form-info:empty { display: none }`, porque una región viva que pasa de `display: none` a visible no se anuncia de forma confiable.

### Mejoras

1. `frontend/src/pages/Seasons.tsx:58`: "Cargando temporadas…" queda pegado al borde izquierdo del panel, porque `.loading-label` solo tiene `padding-block` y está fuera de `panel-body`. Usar `className="muted loading-label panel-body"`: `.loading-label` va después en la cascada y conserva los 24 px verticales.
2. `frontend/src/pages/Seasons.tsx:45,57`:
   - El `ErrorMessage` y "Reintentar" quedan pegados (`panel-body` no tiene `gap`).
   - `useResource` conserva `error` mientras recarga, así que durante el reintento se ven a la vez el error viejo y "Cargando temporadas…".

   Usar `<div className="panel-body form-stack">` con el botón envuelto en un `<div>` (para que no se estire), `loading={temporadas.loading}` en lugar de `disabled`, y no mostrar el `ErrorMessage` cuando `temporadas.loading && !temporadas.data`.
3. `frontend/src/components/CatalogForms.tsx:143`: "Reintentar" no da respuesta mientras consulta, porque la tarjeta sigue en `.warning` hasta que llega la respuesta. Agregar `loading={consulta.loading}`.
4. `frontend/src/components/CatalogForms.tsx:165`: el desglose dice "40 ×1,5", con el signo pegado; la spec pide "40 × 1,5". Usar un helper en `format.ts`, por ejemplo `` producto = (base, f) => `${number(base)} × ${decimal(f)}` ``, o exportar `decimal`.
5. `frontend/src/components/CatalogForms.tsx:138,149,159`: `aria-live` está en tres `div` distintos, uno por estado. Al pasar de "Consultando…" al resultado se monta una región nueva, que no se anuncia de forma confiable. Además, con datos, cada tecla en "Objetivo base" vuelve a anunciar la tarjeta entera, con el enlace y el aviso de cambios sin guardar. Corrección: un único contenedor persistente `div.notice-resolution-card` con `aria-live` que solo cambie su contenido y su clase `warning`, y `aria-atomic="false"`. Si no, sacar `aria-live` de la tarjeta y ponerlo solo en el `p` principal y en `.notice-resolution-breakdown`.
6. `frontend/src/styles/components.css:180` + `CatalogForms.tsx:94`: entre el hint de "Objetivo base" y la tarjeta hay 40 px medidos (24 del `form-stack` más 16 del `margin-top` de la tarjeta). Lo mismo pasa con la tarjeta de avisos. Agregar `.form-stack > .notice-resolution-card { margin-top: 0; }`.
7. `frontend/src/pages/Seasons.tsx:129-130`: el hint queda "…vigente. · 1 de 3 artículos con política Estacional.", con punto y separador juntos. Mejor como oración nueva: `' ' + (k === 1 ? '1 de N artículos tiene' : 'k de N artículos tienen') + ' política Estacional.'`.
8. `frontend/src/pages/Seasons.tsx:125`: sin política, el aviso dice "Pan dulce tiene política sin configurar: …". Para ese caso usar "{Artículo} no tiene política de reposición: esta temporada no cambia sus pedidos hasta que le configures la política Estacional.". El nombre va en `<strong>`, como pide A.3.
9. `frontend/src/pages/Seasons.tsx:152`: "Una categoría" deshabilitada (sin categorías) se ve igual que la habilitada. Agregar en `app.css` `.channel-choice:has(input:disabled) { color: var(--color-disabled); cursor: not-allowed; }`.
10. `frontend/src/styles/app.css:24`: el `legend` conserva el padding del navegador, y "Se aplica a", "Desde" y "Hasta" quedan corridos 2 px respecto de las etiquetas de los campos (se ve en la captura). Agregar `padding: 0` a la regla `legend`.
11. `frontend/src/components/CatalogForms.tsx:104,107`: los `style={{ margin: 0 }}` sobran, porque `.notice-resolution-content p` ya anula el margen. Sacarlos para no dejar estilos en línea en la tarjeta.
