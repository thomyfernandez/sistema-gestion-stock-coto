# Plan — Frontend de gestión de descuentos y promociones por producto

## Contexto

El dominio de valorizacion está completo: `Promocion` (record con tipos `DESCUENTO`, `DOS_POR_UNO`, `MEDIO_PAGO`), `DescuentoDecorator`, `PromocionDecorator` y `ServicioValorizacion` aplican decoradores por producto siguiendo el patrón GoF Decorator.

El backend expone ya cuatro verbos REST bajo `ProductoController`:
- `GET  /api/productos/{id}/promociones`
- `GET  /api/productos/{id}/promociones/{pid}`
- `POST /api/productos/{id}/promociones`
- `PUT  /api/productos/{id}/promociones/{pid}`

`api.ts` expone `api.promociones(id)` y `api.guardarPromocion(id, body, pid?)`.

**Brechas identificadas:**
1. **Backend:** falta `DELETE /api/productos/{id}/promociones/{pid}` en `ProductoController`; falta `eliminarPromocion(int productoId, long promocionId)` en `GestionInventario`; falta `eliminar(long id)` en `RepositorioPromociones`.
2. **Frontend:** falta `api.eliminarPromocion(id, pid)` en `api.ts`; no existe ninguna página de descuentos/promociones.

Archivos **fuera de alcance** (los tocan otros compañeros): `store.tsx`, `Settings.tsx`, `Purchases.tsx`.

---

## Alcance

### Backend (mínimo necesario)
- Agregar `eliminar(long id)` a `RepositorioPromociones`.
- Implementar el adaptador JPA correspondiente.
- Agregar `eliminarPromocion(int productoId, long promocionId)` a `GestionInventario` y su implementación en `GestorInventario`.
- Agregar `DELETE /api/productos/{id}/promociones/{pid}` en `ProductoController` (responde 204 No Content).

### Frontend (trabajo principal)
- `api.eliminarPromocion(id: number, pid: number): Promise<void>` en `api.ts`.
- Nueva página `Descounts.tsx` (slug de ruta `#/descuentos`) con:
  - Selector de producto.
  - Lista de promociones (activas e inactivas) del producto seleccionado.
  - Badge de precio final vs. precio lista (GET `/api/productos/{id}/precio`).
  - Panel/modal para **crear** una promoción (campos según tipo).
  - Acciones por fila: **toggle activa/inactiva** (PUT), **eliminar** (DELETE con confirmación).
  - Visualización de errores de validación del backend (`ApiError.errores`).
- Registrar la vista `descuentos` en el array `views` de `App.tsx` y el subtítulo `'promociones'`.

### Fuera de alcance
- Autenticación y roles (pendiente registrado por separado).
- Paginación del listado de productos (se asume catálogo manejable en el backoffice de una sucursal).

---

## Criterios de aceptación

| # | Criterio |
|---|----------|
| CA1 | El operador puede ver la lista de promociones activas e inactivas de un producto desde la nueva página Descuentos. |
| CA2 | Puede crear una promoción de tipo `DESCUENTO` (porcentaje), `DOS_POR_UNO` o `MEDIO_PAGO` con sus campos correspondientes, y el backend la persiste. |
| CA3 | Puede desactivar/reactivar una promoción existente (toggle activa/inactiva). |
| CA4 | Puede eliminar una promoción. El backend responde 204 y la lista se actualiza. |
| CA5 | El precio mostrado en la vista refleja las promociones activas (usar `GET /api/productos/{id}/precio`). |
| CA6 | Los formularios muestran errores de validación del backend (`problem+json`) en campos concretos. |

---

## Impacto

| Capa | Archivos afectados |
|------|-------------------|
| Dominio (puerto) | `domain/port/RepositorioPromociones.java` — agregar `eliminar` |
| Persistencia | `adapter/jpa/PromocionJpaAdapter.java` (o equivalente) — implementar `eliminar` |
| Servicio | `service/GestionInventario.java` — agregar `eliminarPromocion`; `service/GestorInventario.java` — implementar |
| Controller | `controller/ProductoController.java` — agregar endpoint DELETE |
| Frontend API | `frontend/src/api.ts` — agregar `api.eliminarPromocion` |
| Frontend página | `frontend/src/pages/Descounts.tsx` — nuevo archivo |
| Frontend routing | `frontend/src/App.tsx` — nueva entrada en `views` y `subtitles` |

**No se toca:** `store.tsx`, `Settings.tsx`, `Purchases.tsx`, `ServicioValorizacion.java`, ni los decoradores existentes.

**Patrón GoF:** el Decorator de `ServicioValorizacion` **no se modifica**; el precio final se consulta via `GET /api/productos/{id}/precio` desde el frontend.

---

## Tareas

### `implementador`

#### T1 — Backend: `RepositorioPromociones.eliminar`
- Agregar `void eliminar(long id)` a `domain/port/RepositorioPromociones.java`.
- Implementar en el adaptador JPA (método `deleteById` o equivalente).

#### T2 — Backend: `GestionInventario.eliminarPromocion` + `GestorInventario`
- Agregar `void eliminarPromocion(int productoId, long promocionId)` a `GestionInventario`.
- Implementar en `GestorInventario`: verificar que el producto existe (`exigirStock`), luego `d.promociones.eliminar(promocionId)`.

#### T3 — Backend: endpoint DELETE en `ProductoController`
```java
@DeleteMapping("/{id}/promociones/{promocionId}")
public ResponseEntity<Void> eliminarPromocion(
        @PathVariable @Positive int id,
        @PathVariable @Positive long promocionId) {
    inventario.eliminarPromocion(id, promocionId);
    return ResponseEntity.noContent().build();
}
```
Respuesta: 204 No Content. Errores: 404 si producto o promoción no existe.

#### T4 — Frontend: `api.eliminarPromocion`
```typescript
eliminarPromocion: (id: number, pid: number) =>
  request<void>(`/productos/${id}/promociones/${pid}`, json('DELETE')),
```

#### T5 — Frontend: página `Descounts.tsx`
Estructura mínima de la pantalla:
1. **Encabezado**: selector de producto (lista de `store.data.productos`).
2. **Bloque de precio**: llama `api.precio(id, 1, '')` cuando cambia el producto; muestra precio lista y precio final con ahorro. Se recarga tras toda mutación.
3. **Tabla de promociones**: columnas — Tipo, Porcentaje, Medio de pago, Orden, Estado (badge Activa/Inactiva), Acciones.
4. **Botón "Nueva promoción"**: abre modal/inline form.
5. **Formulario de alta/edición**: campos dinámicos según tipo:
   - `DESCUENTO` → porcentaje (0–100), orden, activa.
   - `DOS_POR_UNO` → orden, activa. Porcentaje fijo 0.
   - `MEDIO_PAGO` → medioPago (texto), porcentaje, orden, activa.
   - Errores de campo provenientes de `ApiError.errores` se muestran bajo cada input.
6. **Toggle activa/inactiva**: PUT con el mismo body salvo `activa` invertida.
7. **Eliminar**: botón con confirmación (diálogo nativo o modal simple); llama `api.eliminarPromocion`.
8. Usa **tokens** del Design System (`var(--…)`); íconos `lucide-react` outline 24 px; grilla de 8 pt.

#### T6 — Frontend: routing en `App.tsx`
- Agregar `{ id: 'descuentos', name: 'Descuentos', icon: Tag }` al array `views`.
- Agregar `descuentos: 'Promociones'` a `subtitles`.
- Renderizar `<DescountsPage tab={route.tab} />` en el bloque de rutas.

---

### `disenador-ux-ui`

#### D1 — Diseño de la página Descuentos
- Flujo de selección de producto → lista de promociones → formulario modal de alta/edición.
- Especificar layout (desktop 1024+ / mobile 390).
- Definir estados vacíos, de carga y de error inline.
- Auditar la UI implementada contra el Design System COTO.

---

### `tester`

#### P1 — Pruebas unitarias de backend (JUnit)
- `GestorInventario.eliminarPromocion`: producto inexistente → 404; promoción inexistente → 404; caso feliz → delega en repositorio.
- `ProductoController.eliminarPromocion` con MockMvc: 204, 404.

#### P2 — Verificación de criterios de aceptación
- CA1 a CA6 verificados manualmente o con scripts de prueba; evidencia en `pruebas.md`.

#### P3 — Build verde
- `mvn -f backend/pom.xml test` en verde.
- `npm run build` en verde.

---

### `reviewer`

#### R1 — Revisión del diff
- Verificar que no se rompió el contrato del Decorator.
- Verificar ausencia de HTTP en componentes React que no sea a través de `api.ts`.
- Verificar uso exclusivo de tokens CSS.
- Verificar respuesta 204 y manejo de `undefined` en `request<void>`.
- Emitir `revision.md`.

---

## Riesgos

| Riesgo | Probabilidad | Mitigación |
|--------|-------------|-----------|
| `RepositorioPromociones` implementado con JPA sin método `delete` expuesto; la entidad puede estar en estado gestionado y requerir `@Transactional` explícito. | Media | El implementador revisa el adaptador existente antes de agregar; si usa `JpaRepository`, `deleteById` ya está disponible. |
| Un 2x1 eliminado mientras está activo puede dejar inconsistencias en cache de `ServicioValorizacion`. | Baja | `ServicioValorizacion` recarga del repositorio en cada llamada (sin cache estático); sin riesgo conocido, verificar en pruebas. |
| El selector de producto en frontend puede ser lento si hay muchos productos (N+1 de promociones). | Media | Cargar promociones solo al seleccionar un producto (lazy); no cargar todas las promociones de todos los productos al iniciar la página. |
| Conflicto de edición concurrente: dos operadores modifican la misma promoción simultáneamente. | Baja | Out of scope; aceptar last-write-wins que ya aplica `guardarPromocion`. |
| `App.tsx` es un archivo compartido; un merge con otra rama puede generar conflicto en `views`. | Media | Coordinarse con el equipo antes de hacer merge; el cambio en `views` es una sola línea de array. |
