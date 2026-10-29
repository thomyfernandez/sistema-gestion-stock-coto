# Design System COTO

Basado en las *Foundations* de COTO (Folcode): color, tipografía, grillas, elevaciones, íconos y anatomía de componentes atómicos. Es la fuente de verdad del `disenador-ux-ui` y de cualquier cambio visual del frontend.

| Capa | Archivo |
| --- | --- |
| Tokens | [`frontend/src/styles/tokens.css`](../../frontend/src/styles/tokens.css) |
| Átomos | [`frontend/src/styles/components.css`](../../frontend/src/styles/components.css) |
| Componentes React | [`frontend/src/components/ui.tsx`](../../frontend/src/components/ui.tsx): `Button`, `IconButton`, `Field`, `Modal`, `Badge`… |
| Catálogo vivo | `cd frontend && npm run dev` → http://127.0.0.1:5173/design-system.html |

## Reglas generales

1. Todo color, sombra, tamaño tipográfico y espaciado sale de un token `var(--…)`. Nada de hex sueltos.
2. Espaciado en múltiplos de 8. Se permiten 4 px solo como ajuste óptico interno de un átomo.
3. Armonía **60 % blanco / 30 % neutros / 10 % primario**. El rojo es para acciones e interactivos, no para decorar.
4. Una sola acción primaria por zona. Las variantes semánticas solo cuando el significado lo pide.
5. Contraste mínimo AA (4.5:1 en texto normal, 3:1 en texto grande y en íconos). Foco visible con `--color-focus`.

## Color System

Paleta inspirada en los colores originales de la marca, con una modificación sutil que los hace más vibrantes. El ratio indica el contraste del texto blanco o del texto oscuro sobre el color.

### Primary

Color predominante de la marca, usado en todos los elementos interactivos: botones, enlaces destacados, entradas activas. Se redujo de 10 a 3 variables.

| Token | Hex | Contraste | Uso |
| --- | --- | --- | --- |
| `--primary-700` | `#A41908` | AAA | Dark: hover y texto rojo sobre blanco |
| `--primary-500` | `#E2001A` | AA 4.94:1 | Base: botón primario, checkbox, radio |
| `--primary-100` | `#FFCCD2` | AAA 9.18:1 con texto oscuro | Light: fondos de selección, halo de hover |

> La lámina indica `#041988` para Primary 700, pero ese valor es azul y no coincide con su muestra (rojo oscuro, AAA 8.88). Se interpreta como un error de tipeo y se usa `#A41908`, el valor con el que ya trabajaba el proyecto. Confirmar con diseño.

### Neutrals / Grays

Gris con un pequeño tono rojizo: base de texto, campos de formulario, fondos y divisores.

| Token | Hex | Contraste | Uso sugerido |
| --- | --- | --- | --- |
| `--neutral-900` | `#1B171C` | AAA 17.7:1 | Texto principal, botón secondary fill |
| `--neutral-800` | `#352F37` | AAA 13.01:1 | Títulos secundarios, hover oscuro |
| `--neutral-700` | `#504653` | AAA 8.96:1 | Texto secundario, íconos |
| `--neutral-600` | `#6A5E6E` | AAA 6.1:1 | Texto terciario, ayudas |
| `--neutral-500` | `#1A171B` | AAA 17.76:1 | Base |
| `--neutral-400` | `#9D91A1` | AA 5.9:1 | Disabled |
| `--neutral-300` | `#B6ACB9` | AA 8.08:1 | Íconos deshabilitados |
| `--neutral-200` | `#CEC8D0` | AA 10.79:1 | Bordes de campos y botones lineales |
| `--neutral-100` | `#E7E3E8` | AAA 13.95:1 | Divisores |
| `--neutral-50` | `#F3F1F3` | AAA 15.75:1 | Fondo de la aplicación (BG) |
| `--neutral-00` | `#FFFFFF` | AAA 17.7:1 | Superficies (White) |

Los ratios de 300 a 00 de la lámina se miden con texto oscuro sobre el color.

### Semantic / Feedback

| Familia | Tokens | Hex | Significado |
| --- | --- | --- | --- |
| Info | `--info-800` / `--info-500` / `--info-100` | `#17274F` / `#284387` / `#D8DFF3` | Información, enlaces, foco |
| Success | `--success-900` / `--success-500` / `--success-100` | `#033300` / `#055E00` / `#CFFFCC` | Acción positiva, tendencia positiva, confirmación exitosa |
| Warning | `--warning-800` / `--warning-500` / `--warning-100` | `#665000` / `#DDAD00` / `#FFE27B` | Acción potencialmente destructiva o "en espera"; confirmaciones |
| Danger | `--danger-800` / `--danger-500` / `--danger-100` | `#622004` / `#D34508` / `#FDDCCE` | Errores y acciones destructivas |

Los tonos 100 son fondos de mensajes y badges; los 500 rellenan botones y puntos de estado; los 800/900 se usan para texto sobre los fondos 100.

### Roles semánticos

Para no atar componentes a un tono concreto, `tokens.css` define alias: `--color-text`, `--color-text-muted`, `--color-text-subtle`, `--color-surface`, `--color-background`, `--color-border`, `--color-divider`, `--color-action`, `--color-action-hover`, `--color-link`, `--color-focus` y `--color-disabled`. Preferirlos cuando el significado coincide.

## Typography

Familia **Gotham Rounded** en títulos y cuerpo, por su coherencia con la identidad visual, legibilidad y versatilidad. Es una fuente comercial: se declara primero y se usa si está instalada; el build distribuye **Nunito Variable** como alternativa redondeada libre.

| Estilo | Token | Tamaño / interlínea | Peso | Clase |
| --- | --- | --- | --- | --- |
| Display Large | `--display-large` | 56 px / 140 % | Bold | `.display-lg` |
| Display Medium | `--display-medium` | 40 px / 140 % | Bold | `.display-md` |
| Display Small | `--display-small` | 32 px / 140 % | Bold | `.display-sm` |
| Heading 1 | `--heading-h1` | 28 px / 140 % | Semibold | `.heading-h1` |
| Heading 2 | `--heading-h2` | 24 px / 140 % | Semibold | `.heading-h2` |
| Heading 3 | `--heading-h3` | 22 px / 140 % | Semibold | `.heading-h3` |
| Heading 4 | `--heading-h4` | 20 px / 140 % | Semibold | `.heading-h4` |
| Body large | `--body-large` | 18 px | Bold / Semibold / Regular | `.body-lg` + peso |
| Body | `--body-medium` | 16 px | Bold / Semibold / Regular / Book | `.body-md` + peso |
| Caption | `--caption` | 12 px | Bold / Semibold / Regular | `.caption-text` + peso |

Pesos: `--weight-bold` 700, `--weight-semibold` 600, `--weight-regular` 400, `--weight-book` 350.

## Grillas y espaciado

Grilla de 8 puntos: todos los elementos y sus separaciones se construyen con múltiplos de 8.

**Grilla desktop**: pantalla de 1024 px, márgenes de 24 px a cada lado (976 px disponibles), 9 columnas de 94 px con carriles de 16 px. Se divide en **zona principal de 645 px** (contenido y tablas) y **zona secundaria de 315 px** (resúmenes, filtros, vista previa). En móvil se apila en una sola columna con márgenes de 16 px.

| Token | Valor |
| --- | --- |
| `--grid-width` / `--grid-content` / `--grid-margin` | 1024 / 976 / 24 px |
| `--grid-columns` / `--grid-column` / `--grid-gutter` | 9 / 94 / 16 px |
| `--zone-main` / `--zone-secondary` | 645 / 315 px |

**Espaciado**: `--space-1` 8 · `--space-2` 16 · `--space-3` 24 · `--space-4` 32 · `--space-5` 40 · `--space-6` 48 · `--space-7` 56 · `--space-8` 64 · `--space-10` 80. La jerarquía se da por el tamaño del espacio: 8 dentro de un componente, 16 entre campos, 24 entre bloques y 32 o más entre secciones.

## Elevations

Sombras con la lógica de Material Design: cuanto más proyectada la sombra, más elevado el elemento y mayor su jerarquía visual.

| Token | Uso |
| --- | --- |
| `--shadow-100` | Estado default: cards, paneles, botones |
| `--shadow-200` | Superficies apoyadas sobre otra superficie |
| `--shadow-300` | Modales y popups |
| `--shadow-400` | Hover / active |
| `--shadow-500` a `--shadow-700` | Menús flotantes, toasts y capas superiores |

Clases utilitarias: `.elevation-100` … `.elevation-700`.

## Icon System

Se seleccionó **Untitled UI**, de base morfológica Feather, solo en versión outline. Los trazos redondeados acompañan a los botones y a la tipografía y transmiten una identidad amigable. En el código se implementa con `lucide-react`, también derivado de Feather, con trazo de 2 px y terminaciones redondeadas, para no sumar dependencias. Un reemplazo por `@untitledui/icons` mantiene los mismos tamaños.

| Tamaño | Token | Uso |
| --- | --- | --- |
| Big | `--icon-big` 48 px | Estados vacíos, ilustraciones |
| Medium | `--icon-medium` 32 px | Indicadores, encabezados de tarjeta |
| Small | `--icon-small` 24 px | Botones, tablas, navegación |

Los íconos decorativos llevan `aria-hidden="true"`. Un botón que solo tiene ícono necesita `aria-label`.

## Componentes atómicos

Los átomos son las piezas mínimas que, combinadas, construyen la interfaz.

### Buttons

`<Button variant size icon iconPosition loading>` en `ui.tsx`, clases `.button.<variante>` en `components.css`.

| Grupo | `variant` | Apariencia | Cuándo |
| --- | --- | --- | --- |
| Primary | `primary` | Fill Primary 500, hover Primary 700 + sombra 400 | Acción principal de la zona |
| Primary | `lineal` | Borde y texto Primary 500 sobre blanco | Alternativa a la primaria con peso de marca |
| Secondary | `dark` | Fill Neutral 900 | Acción fuerte que no es la principal |
| Secondary | `secondary` | Lineal neutro sobre blanco | Acciones secundarias: Cerrar, Volver, Cancelar |
| — | `ghost` | Sin borde ni fondo | Acciones terciarias o en barras |
| Semantics | `warning` | Fill Warning 500, texto oscuro | Confirmar algo potencialmente destructivo |
| Semantics | `success` | Fill Success 500 | Confirmar un resultado positivo |
| Semantics | `danger` | Fill Danger 500 | Eliminar, anular, cancelar definitivamente |
| Semantics | `info` | Fill Info 500 | Acciones informativas |
| Disabled | cualquiera + `disabled` | Fill Neutral 400 (rellenos) o trazo gris (lineales) | No disponible |

- **Tamaños**: `size="lg"` 56 px (Big), `md` 44 px (Medium, por defecto) y `sm` 40 px (Small).
- **Ícono**: `icon` a la izquierda por defecto, `iconPosition="right"` para avanzar o navegar.
- **Header button**: `className="header-button"`, ícono sobre etiqueta en caption, para la barra superior (por ejemplo, Ayuda).

> En la lámina, el botón Warning lleva texto blanco sobre `#DDAD00`, un contraste de 2:1 que no cumple AA. Se implementa con texto Neutral 900.

### Links

`<a className="link">`: Info 500 subrayado, hover Info 800. Tamaños `.link.lg` 18 px, `.link` 16 px y `.link.sm` 12 px. Los enlaces de navegación interna con ícono siguen usando `.text-link`.

### Icon buttons

`<IconButton icon label appearance size>`: `naked` (solo ícono), `lineal` (círculo con borde) y `fill` (círculo relleno Neutral 700). Tamaños `sm` 24, `md` 32 y `lg` 40 px. En estado disabled pasan a Neutral 300. En zonas táctiles se usa `lg` para respetar un objetivo de 40 px.

### Checkbox & Radio buttons

Se estilizan los `input[type=checkbox]` y `input[type=radio]` nativos, sin componentes extra: trazo Primary 500 y relleno Primary 500 al marcar. En hover, Primary 700 con un halo Primary 100; deshabilitados en Neutral 300. Para asociar la etiqueta, `<label className="choice">`.

### Keypad

`.keypad` + `.keypad-key`: teclas de 40 px con borde Neutral 900. La tecla de acción (borrar) usa `.keypad-key.fill`. Pensado para cargar cantidades en dispositivos táctiles de sucursal.

## Cambios respecto de la versión anterior de los tokens

Se alinearon a las láminas los valores que diferían: Neutral 900, 800, 700, 600, 300 y 200; Info 500 y 100; Success 500; Warning 500 y 100; Danger 800, 500 y 100. `--info-900` pasó a llamarse `--info-800`, como en la lámina. También se agregaron los tokens de espaciado, grilla, roles semánticos, radio `pill` e `--icon-stroke`, y los átomos Lineal, Dark, semánticos, tamaños, Header button, Link, Icon button, Radio y Keypad.
