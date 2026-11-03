# Diseño UX/UI — Página Descuentos y Promociones

> **Agente:** `disenador-ux-ui`  
> **Pedido:** `descuentos`  
> **Fecha:** 2026-11-03  
> **Archivo de referencia de layout:** `frontend/src/pages/Purchases.tsx` (tab `promociones` → componente `Promotions`)  
> **Tipo:** `Promocion` → `frontend/src/types.ts` línea 17

---

## 1. Flujo del operador

```
[Página Descuentos]
       │
       ▼
[1] Seleccioná un artículo del selector (select nativo, filtrado por nombre)
       │
       ├─ Sin productos → Empty "El catálogo está vacío" con enlace a Compras
       │
       ▼
[2] Se carga la lista de promociones del artículo (GET /api/productos/{id}/promociones)
       │
       ├─ Cargando → skeleton/label "Cargando promociones…"
       ├─ Error API → ErrorMessage con mensaje accionable
       └─ OK ──────────────────────────────────────────────────────────────────────────────────────────────
              │
              ├─ Sin promociones → Empty "Sin promociones" + botón "Nueva promoción"
              │
              └─ Con promociones → Tabla de promociones
                      │
                      ├─[A] Clic "Nueva promoción" ────────────────────────────────────────┐
                      │                                                                     │
                      ├─[B] Clic "Editar" en una fila ─────────────────────────────────┐   │
                      │                                                                 │   ▼
                      │                                                        [Modal PromocionForm]
                      │                                                                 │
                      │                                              ┌──── OK → cierra modal, refresca lista
                      │                                              │     (badge de éxito en toast del store)
                      │                                              └──── Error → ErrorMessage dentro del modal
                      │                                              └──── Cancelar → cierra modal (foco vuelve al botón)
                      │
                      ├─[C] Toggle activa/inactiva (switch en fila)
                      │         PUT /api/productos/{id}/promociones/{pid}
                      │         body = { ...promocion, activa: !activa }
                      │         OK → lista se refresca | Error → ErrorMessage inline
                      │
                      └─[D] Clic "Eliminar" en una fila
                                │
                                ▼
                        [Modal Confirmation danger]
                        "¿Eliminar la promoción?"
                        "Esta acción no se puede deshacer."
                                │
                                ├─ "Eliminar" → DELETE /api/productos/{id}/promociones/{pid}
                                │               204 OK → cierra modal, refresca lista
                                │               Error → mensaje en errorRef del Confirmation
                                └─ "Volver"  → cierra modal, foco vuelve al botón "Eliminar"
```

---

## 2. Estructura de pantalla

### Desktop 1024 px

Usa la grilla de 9 columnas (`--zone-main` 645 px + `--zone-secondary` 315 px), márgenes de 24 px.

```
┌─────────────────────────────────────────────────────────────────────────┐
│  PageHeading                                                            │
│  eyebrow="Backoffice"   title="Descuentos"                              │
│  text="Administrá los descuentos y promociones de cada artículo."       │
│  actions=[ <Button variant="primary" icon={Plus}>Nueva promoción</Button> ] │
└─────────────────────────────────────────────────────────────────────────┘

┌─── zona-main (645 px) ───────────────────────────┐  ┌─── zona-secondary (315 px) ─┐
│  .panel                                          │  │  .panel.simulator           │
│  ┌─────────────────────────────────────────────┐ │  │  Simular precio             │
│  │ panel-heading                               │ │  │  ─────────────────          │
│  │  h2 "Promociones acumulables"               │ │  │  Unidades: [input number]   │
│  │  p  "Los beneficios se aplican en orden."   │ │  │  Medio de pago: [select]    │
│  │  [Button primary "Nueva promoción"]         │ │  │  ─────────────────          │
│  └─────────────────────────────────────────────┘ │  │  quote-box                  │
│  ┌─────────────────────────────────────────────┐ │  │  · Precio lista: $XXX       │
│  │ panel-body                                  │ │  │  · Ahorro: −$XX             │
│  │  Field "Artículo" → <select>                │ │  │  · Total final: $XXX        │
│  └─────────────────────────────────────────────┘ │  └─────────────────────────────┘
│  [tabla de promociones o Empty]                  │
└──────────────────────────────────────────────────┘
```

**Nota:** El simulador de precio en la zona secundaria (panel derecho) **ya existe** en `Purchases.tsx` como componente `PriceSimulation`. El implementador debe reutilizarlo tal cual, pasando `productId`, `cantidad` y `medioPago`.

### Mobile 390 px

Columna única, márgenes de 16 px. El panel principal ocupa el ancho completo; el simulador se apila debajo en un `<details>` colapsable con `<summary>Simular precio</summary>` para no obstruir la lista.

```
┌────────────────────────────┐
│  PageHeading               │
│  title="Descuentos"        │
│  (sin eyebrow en mobile)   │
└────────────────────────────┘
┌────────────────────────────┐
│  .panel                    │
│  Field "Artículo"          │
│  [Button primary full-width│
│   "Nueva promoción"]       │
│  ─────────────────         │
│  tabla (scroll horizontal  │
│  .table-scroll)            │
└────────────────────────────┘
┌────────────────────────────┐
│  <details> Simular precio  │
│  (colapsado por defecto)   │
└────────────────────────────┘
```

---

## 3. Componentes reutilizados

| Componente | Variante / tamaño | Dónde se usa |
|---|---|---|
| `PageHeading` | `eyebrow`, `title`, `text`, `actions` | Encabezado de página |
| `Button` | `primary` / `md` / `icon={Plus}` | "Nueva promoción" en heading y en panel-heading |
| `Button` | `ghost` / `md` / `icon={Edit3}` | Acción "Editar" por fila |
| `Button` | `ghost` / `md` / `icon={Trash2}` | Acción "Eliminar" por fila |
| `Button` | `secondary` / `md` | "Cancelar" / "Volver" en modales |
| `Button` | `danger` / `md` | Confirmar eliminación en `Confirmation` |
| `Field` | default | Todos los campos del formulario |
| `Modal` | default (`wide=false`) | Formulario de alta/edición de promoción |
| `Badge` | `tone="success"` | Promoción activa |
| `Badge` | `tone="neutral"` | Promoción inactiva |
| `Badge` | `tone="warning"` | Tipo `DOS_POR_UNO` (sin porcentaje, resaltar) |
| `Empty` | `title`, `text`, `action` | Sin productos en catálogo; sin promociones del artículo |
| `ErrorMessage` | default | Errores de API dentro del panel y dentro del modal |
| `Confirmation` | `danger=true` | Confirmar eliminación de promoción |
| `Metric` | — | **No se usa** en esta página (el simulador reemplaza ese rol) |

**Componentes existentes adicionales reutilizados:**
- `PriceSimulation` (ya en `Purchases.tsx`, componente privado) — extraer o duplicar según convenio del equipo.  
  Si el implementador prefiere no duplicar lógica, moverlo a `components/PriceSimulation.tsx` como componente compartido.
- `useResource` (hook) — para cargar promociones con señal de cancelación.
- Switch nativo (`input[type=checkbox][role=switch]`) ya en uso en `Purchases.tsx`; reutilizar el mismo patrón con clase `switch`.

---

## 4. Formulario dinámico (PromocionForm)

### Campos por tipo

El campo **Tipo** es siempre el primero. Al cambiar el tipo, los campos condicionales aparecen/desaparecen inmediatamente (sin recargar el modal).

| Campo | `DESCUENTO` | `DOS_POR_UNO` | `MEDIO_PAGO` |
|---|:---:|:---:|:---:|
| Tipo (select) | ✓ | ✓ | ✓ |
| Porcentaje (number) | ✓ | ✗ | ✓ |
| Medio de pago (text) | ✗ | ✗ | ✓ |
| Orden (number) | ✓ | ✓ | ✓ |
| Activa (checkbox) | ✓ | ✓ | ✓ |

**Para `DOS_POR_UNO`**: el implementador envía `porcentaje: 0` al backend (valor fijo, invisible para el operador).

### Opciones del select de Tipo

```
Tipo de promoción
  ─ Descuento porcentual
  ─ 2×1 en unidades
  ─ Descuento por medio de pago
```

Los valores internos son `DESCUENTO`, `DOS_POR_UNO`, `MEDIO_PAGO`.

### Validaciones visibles y mensajes de error

Todas las validaciones se muestran **debajo del campo** (`field-hint` en rojo o `ErrorMessage` si vienen del backend). Todos los mensajes en voseo.

| Campo | Validación | Mensaje de error |
|---|---|---|
| Tipo | Requerido | — (siempre hay valor seleccionado) |
| Porcentaje | Requerido si aplica; número entre 1 y 100 | "El porcentaje tiene que estar entre 1 y 100." |
| Porcentaje | Entero | "Ingresá un número entero." |
| Medio de pago | Requerido si tipo = `MEDIO_PAGO`; máx. 50 caracteres | "El medio de pago es obligatorio." / "El medio de pago no puede superar los 50 caracteres." |
| Orden | Requerido; entero ≥ 0 | "El orden tiene que ser un número entero mayor o igual a cero." |
| Error de API (campo) | `ApiError.errores` | Se muestra bajo el campo correspondiente con clase `field-error` (ver §5) |
| Error de API (general) | `ApiError.message` | `<ErrorMessage error={error} />` al pie del modal, antes del footer |

**Orden por defecto:** `Math.max(0, ...promos.map(p => p.orden)) + 10` (igual que en `Purchases.tsx`).  
**Activa por defecto:** `true`.

### Estructura del modal

```
Modal title="Nueva promoción" | "Editar promoción"
  modal-body form-stack
    Field "Tipo de promoción"     → <select>
    Field "Porcentaje" (condicional) → <input type="number" min=1 max=100 step=1>
    Field "Medio de pago" (condicional) → <input type="text" maxLength=50>
    Field "Orden de aplicación"   → <input type="number" min=0 step=10>
    label.choice
      <input type="checkbox"> Promoción activa
    <ErrorMessage error={error} />
  modal-footer
    <Button secondary>Cancelar</Button>
    <Button primary type="submit" loading={busy}>Guardar promoción</Button>
```

---

## 5. Estados de la página

### 5.1 Vacío — sin productos en el catálogo

```
<Empty
  title="El catálogo está vacío"
  text="Creá al menos un artículo antes de configurar promociones."
  action={<Button variant="lineal" onClick={() => navigate('#/compras/catalogo')}>
            Ir al catálogo
          </Button>}
/>
```

### 5.2 Cargando promociones

```html
<p class="muted loading-label">Cargando promociones…</p>
```
(mismo patrón que `Promotions` en `Purchases.tsx`)

### 5.3 Error de API

```tsx
<ErrorMessage error={promos.error} />
```
El mensaje incluye texto accionable: "No se pudo cargar la lista de promociones. Revisá la conexión y volvé a intentarlo."

### 5.4 Éxito (mutaciones)

Se delega al `store.run` / `store.act` que ya emite el toast del sistema con `role="status"`. No se necesita UI adicional.

### 5.5 Badge de estado con color semántico

| Estado | Tono del Badge | Texto |
|---|---|---|
| Activa | `success` | Activa |
| Inactiva | `neutral` | Inactiva |

### 5.6 Confirmación de eliminación

```tsx
<Confirmation
  danger
  title="Eliminar promoción"
  text="¿Seguro que querés eliminar esta promoción? Esta acción no se puede deshacer."
  label="Eliminar"
  onClose={...}
  onConfirm={() => run(() => api.eliminarPromocion(productoId, promoId), 'Promoción eliminada.')}
/>
```

---

## 6. Tabla de promociones

### Columnas

| # | Encabezado | Contenido | Clase CSS |
|---|---|---|---|
| 1 | Orden | número de orden (ej. `10`) | `numeric` |
| 2 | Tipo | texto legible (ver abajo) | — |
| 3 | Porcentaje | `XX %` o `—` si tipo `DOS_POR_UNO` | `numeric` |
| 4 | Medio de pago | valor de `medioPago` o `—` | — |
| 5 | Estado | `<Badge tone>Activa / Inactiva</Badge>` | — |
| 6 | `<span class="sr-only">Acciones</span>` | switch + editar + eliminar | — |

**Textos de tipo:**
```
DESCUENTO    → "Descuento porcentual"
DOS_POR_UNO  → "2×1 en unidades"
MEDIO_PAGO   → "Por medio de pago"
```

### Acciones por fila

```
[switch activa/inactiva]  [IconButton naked Edit3 "Editar"]  [IconButton naked Trash2 "Eliminar"]
```

Los `IconButton` usan `size="lg"` (40 px) para el objetivo táctil ≥ 40 px.  
`aria-label` incluye el ID: `"Editar promoción 7"`, `"Eliminar promoción 7"`.

### Fila inactiva

Clase `.inactive` en el `<tr>`: `opacity: 0.55` (ya existe el patrón en `Purchases.tsx` con `.promotion-row.inactive`). Mantener el mismo token.

### Scroll horizontal

```tsx
<div class="table-scroll" role="region" aria-label="Promociones del artículo" tabIndex={0}>
  <table> … </table>
</div>
```

---

## 7. Selector de producto

Se usa un `<select>` nativo envuelto en `<Field label="Artículo">`, idéntico al de `Promotions` en `Purchases.tsx`:

```tsx
<Field label="Artículo">
  <select value={productId} onChange={e => setProductId(Number(e.target.value))}>
    {data.productos.map(p => (
      <option key={p.id} value={p.id}>{p.nombre}</option>
    ))}
  </select>
</Field>
```

**Justificación:** el backoffice de una sucursal maneja un catálogo de tamaño razonable (< 500 artículos). Un `<select>` nativo cumple la accesibilidad de teclado y táctil sin necesidad de un componente de autocompletado adicional.  
**Si el equipo decide implementar un `<input type="search">` filtrando el listado:** especificarlo como pendiente en el siguiente sprint; para esta iteración no se necesita.

---

## 8. Contenido — textos en español rioplatense

### Encabezado de página

```
eyebrow:  "Backoffice"
title:    "Descuentos"
text:     "Administrá los descuentos y promociones por artículo."
```

### Panel de promociones

```
h2:  "Promociones acumulables"
p:   "Los beneficios se aplican en el orden que definís."
```

### Botones

```
"Nueva promoción"          (Button primary, PageHeading y panel-heading)
"Guardar promoción"        (Button primary, modal footer)
"Cancelar"                 (Button secondary, modal footer)
"Volver"                   (Button secondary, Confirmation footer)
"Eliminar"                 (Button danger, Confirmation footer)
"Editar"                   (Button ghost, solo texto visible en viewport)
```

### Mensajes de error accionables

| Situación | Mensaje |
|---|---|
| No carga promociones | "No se pudieron cargar las promociones. Revisá la conexión y volvé a intentarlo." |
| Error al guardar | "No se pudo guardar la promoción. Corregí los errores y volvé a intentarlo." |
| Error al eliminar | "No se pudo eliminar la promoción. Volvé a intentarlo en unos segundos." |
| Error al togglear | "No se pudo cambiar el estado de la promoción. Volvé a intentarlo." |
| Catálogo vacío | "Creá al menos un artículo antes de configurar promociones." |
| Sin promociones | "Todavía no hay promociones para este artículo. Agregá la primera." |

### Textos del simulador (zona secundaria)

```
h2:  "Simular precio"
p:   "Consultá cómo se acumulan los beneficios para este artículo."
label Unidades: "Unidades a valorizar"
label Medio de pago: "Medio de pago"
hint: "El precio de lista no se modifica. La simulación no hace reservas."
```

---

## 9. Accesibilidad

| Requisito | Implementación |
|---|---|
| Contraste AA (4.5:1 texto normal) | Todos los colores de texto usan tokens (`--color-text`, `--neutral-900`, `--neutral-700`). Badge `success` usa fondo `--success-100` con texto `--success-900`. Badge `neutral` usa `--neutral-100` con `--neutral-700`. Relación ≥ 5:1 en ambos. |
| Contraste AA (3:1 texto grande / íconos) | Los `IconButton` con `--neutral-700` sobre `--neutral-00` cumplen 6.1:1. |
| Foco visible | Todos los interactivos heredan el outline `--color-focus` (info 500) del DS. No sobreescribir `outline: none` sin reemplazarlo. |
| `aria-*` en tabla | `role="region"` + `aria-label="Promociones del artículo"` en `.table-scroll`. `scope="col"` en cada `<th>`. Columna de acciones con `<span class="sr-only">Acciones</span>`. |
| `aria-label` en botones de ícono | `"Editar promoción {id}"`, `"Eliminar promoción {id}"`, `"Activar / Desactivar promoción {id}"`. |
| `aria-live` para actualizaciones | El `.quote-box` del simulador lleva `aria-live="polite"`. El sistema de toasts del store ya tiene `role="status"`. |
| Modal accesible | `<dialog>` nativo con `aria-labelledby={titleId}` (ya implementado en `Modal`). Foco trampa manejado por el browser. Al cerrar, el foco retorna al disparador (ya en el `useEffect` del `Modal`). |
| Objetivos táctiles ≥ 40 px | `Button` default `min-height: 44px`. `IconButton size="lg"` → 40 px. Switch: área de toque del label ≥ 40 px (ver §10). `<select>` hereda `min-height: 44px` del reset del DS. |
| Keyboard navigation | El `<select>` de producto es navegable con teclado. La tabla con `.table-scroll[tabIndex=0]` es enfocable y scrollable con teclado. |
| `aria-disabled` vs `disabled` | Usar `disabled` nativo; el DS ya aplica los estilos de disabled correctos. |

---

## 10. Responsive

### Desktop 1024 px

- Layout de dos columnas: `.promotion-layout` con `display: grid; grid-template-columns: var(--zone-main) var(--zone-secondary); gap: var(--space-3)`.
- La tabla ocupa el ancho completo de la zona principal.
- El simulador ocupa la zona secundaria, siempre visible.

### Mobile 390 px

- `.promotion-layout` colapsa a columna única: `grid-template-columns: 1fr`.
- El simulador se envuelve en `<details>` con `<summary>Simular precio</summary>`, colapsado por defecto.
- El botón "Nueva promoción" en el `panel-heading` usa `width: 100%` en mobile (clase `.block` del Button).
- La tabla mantiene scroll horizontal (`.table-scroll` ya tiene `overflow-x: auto`).
- Los `IconButton` de acciones se mantienen en `size="lg"` (40 px) para objetivos táctiles adecuados.
- El switch de activa/inactiva debe tener una zona de toque de al menos 40 × 40 px: el `<label class="switch">` necesita `min-height: 40px; display: inline-flex; align-items: center`.

---

## 11. Tokens y átomos nuevos

### Tokens nuevos: ninguno

Todos los colores, espaciados, tipografía y elevaciones necesarios ya existen en `tokens.css`.

### Átomos nuevos: ninguno

Los componentes de `ui.tsx` cubren todos los casos. La clase `.inactive` en filas de tabla ya existe en `app.css`/`shell.css` (referenciada en `Purchases.tsx`).

### Clase CSS local nueva: `.field-error`

Para los errores de campo provenientes de `ApiError.errores` (complementa al `<ErrorMessage>` general). Se agrega en el archivo de estilos de la página (`Descounts.tsx` puede usar estilos inline o el archivo de la aplicación), **no en `components.css`**:

```css
/* En el scope de la página Descuentos o en app.css */
.field-error {
  color: var(--danger-800);
  font-size: var(--caption);
  margin-top: var(--space-half);
}
```

> **Alternativa aprobada:** usar directamente `<span class="field-hint">` con estilo de error, ya que el DS define hints con `color: var(--color-text-subtle)`. Si el implementador prefiere no agregar la clase, puede usar `<ErrorMessage>` solo al nivel del formulario completo y listar los errores de campo como `<ul>` dentro del mensaje (ya soportado por `ErrorMessage` cuando `ApiError.errores` tiene entradas).

---

## 12. Decisiones de diseño a replicar

Las siguientes decisiones se registran en la memoria del equipo para que otras pantallas las sigan:

1. **Select nativo para selector de entidad:** cuando el catálogo es de tamaño manejable (< 500 ítems), se usa `<select>` nativo envuelto en `<Field>`. Sin combobox personalizado.
2. **Formulario modal dinámico por tipo:** los campos se muestran/ocultan según el `type` seleccionado dentro del mismo modal, sin cambiar de modal ni de paso.
3. **Simulador en zona secundaria + colapsable en mobile:** el panel de simulación de precio va siempre a la derecha en desktop y dentro de un `<details>` colapsado en mobile.
4. **Badge de estado en tabla:** `tone="success"` para activo, `tone="neutral"` para inactivo (no usar `tone="danger"` para inactivo, que se reserva para errores).
5. **Eliminación siempre con `Confirmation danger`:** toda acción de borrado usa el componente `Confirmation` con `danger=true`, nunca un `confirm()` nativo del browser.

---

## 13. Puntos que el implementador debe respetar estrictamente

1. **HTTP solo en `api.ts`:** `api.promociones`, `api.guardarPromocion` y `api.eliminarPromocion`. Ningún `fetch` directo en el componente.
2. **Estado en el store:** usar `store.run` y `store.act` para las mutaciones; `useResource` para la carga lazy de promociones por producto.
3. **Ruta:** `#/descuentos` registrada en `App.tsx` con `{ id: 'descuentos', name: 'Descuentos', icon: Tag }` y subtítulo `'Promociones'`.
4. **Archivo de página:** `frontend/src/pages/Descounts.tsx` (nombre tal como define el plan, con la `u` inglesa).
5. **Tokens:** cero colores hex sueltos; cero `px` de espaciado que no sean múltiplos de 8 (salvo ajustes ópticos de 4 px con `--space-half`).
6. **Íconos:** `lucide-react` outline 24 px (`size={24}`). Para el estado vacío se puede usar `Tag` (48 px, `--icon-big`).
7. **`DOS_POR_UNO`:** el campo `porcentaje` no se muestra en el formulario; el implementador envía siempre `porcentaje: 0` al backend para ese tipo.
8. **Eliminar:** respuesta 204 No Content; `api.eliminarPromocion` retorna `Promise<void>`; el implementador debe manejar que `request<void>` puede resolver `undefined`.
9. **Refresco:** toda mutación (create, update, toggle, delete) debe disparar el refresco de la lista de promociones del artículo seleccionado, usando `store.run` que ya incrementa `revision` y `useResource` que ya depende de `revision`.
10. **Mobile:** el layout en mobile debe ser columna única sin overflow horizontal en el contenedor principal (la tabla en sí tiene scroll horizontal permitido).
