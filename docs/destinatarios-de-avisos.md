# Destinatarios de avisos: listas de emails

Los avisos de stock bajo se envían por email a **listas de direcciones por categoría** y a una **lista general** de la sucursal. Ya no se configuran en el modal de política de reposición: ese modal queda con los parámetros de reposición y el umbral de alerta del artículo.

## Uso

1. Abrir **Configuración → Alertas y avisos → Destinatarios de avisos**.
2. Elegir **Editar** en la lista general o en la fila de una categoría.
3. Cargar los emails, uno por línea o separados por coma. Se aceptan hasta 20 direcciones por lista; se guardan en minúsculas y sin duplicados. Dejar el campo vacío vacía la lista.
4. Guardar. La tabla muestra cuántas direcciones tiene cada lista y, para las categorías sin emails, si usan la lista general o el respaldo.

En **Compras → Reposición**, la columna *Avisar a* y el modal de cada política muestran a qué lista o contacto irá el próximo aviso.

## A quién se avisa

Para cada artículo que cae por debajo de su umbral:

1. **Lista de su categoría**, si tiene al menos un email.
2. **Lista general**, si la categoría no tiene emails o el artículo no tiene categoría.
3. **Respaldo**, si ninguna lista tiene emails: el contacto particular que el artículo tenía asignado antes de este cambio, el contacto de su categoría y, por último, el destinatario de la sucursal (**Respaldo de la sucursal**, que admite SMS y WhatsApp).

Se genera **un evento por dirección** en la outbox (`evento_integracion`). Cada email se entrega y reintenta por separado; una casilla con error no detiene a las demás. Igual que antes, los avisos pendientes conservan las direcciones con las que se crearon: editar una lista no reescribe envíos ya encolados.

El respaldo no borra ninguna configuración previa. Las asignaciones de contacto por artículo se conservan en la base y se siguen guardando sin cambios al editar la política, pero sólo se usan mientras no haya listas con emails. Para dejar de usarlas, alcanza con cargar la lista general.

En los datos de demostración, Verdulería ya tiene dos direcciones (`verduleria@coto.example` y `jefe.verduleria@coto.example`); la lista general arranca vacía y el resto usa `compras@coto.example`.

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| GET | `/api/configuracion/destinatarios` | Lista general (siempre primera, `categoriaId: null`) y las categorías con al menos un email |
| PUT | `/api/configuracion/destinatarios/general` | Reemplaza la lista general: `{ "emails": ["sucursal@coto.example"] }` |
| PUT | `/api/configuracion/destinatarios/categorias/{id}` | Reemplaza la lista de la categoría. `404` si no existe |

Los PUT requieren rol `SUPERVISOR`, como el resto de `/api/configuracion`. Emails inválidos, más de 20 direcciones o un cuerpo sin `emails` responden `400` en `application/problem+json` sin modificar lo guardado.

## Persistencia y migración

Tabla `destinatario_aviso (id, categoria_id, email)` con `UNIQUE (categoria_id, email)`; `categoria_id` nulo es la lista general. El dominio (`ListaDestinatarios`) valida y normaliza; `ServicioDestinatarios` reemplaza la lista completa bajo el mismo bloqueo de inventario que crea los avisos.

Instalación nueva: `schema.sql` y `data.sql` ya incluyen la tabla. Base existente, una sola vez y después de `upgrade-auth.sql`:

```sh
psql -v ON_ERROR_STOP=1 -1 -d coto_stock -f backend/src/main/resources/db/upgrade-destinatarios.sql
```

El script es idempotente y no copia ni modifica asignaciones anteriores: hasta cargar una lista, los avisos salen exactamente como antes.

## Validación

- `ApiRestTest`: listas por categoría y general, normalización, un email por dirección, prioridad sobre el contacto anterior, vuelta al respaldo al vaciar, validaciones y conservación de direcciones en avisos pendientes.
- `UpgradeDestinatariosTest`: cadena completa desde v0.1, idempotencia y restricciones de la tabla.
- `PostgresRealTest`: el mismo flujo contra PostgreSQL 17.
- Frontend: `Settings.test.tsx`, `PolicyForm.test.tsx`, `notifications.test.ts` y el recorrido de Playwright *destinatarios*.
