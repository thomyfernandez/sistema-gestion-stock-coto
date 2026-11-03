# Revisión de código — Descuentos y Promociones

## Veredicto
APROBADO

## Hallazgos
No se encontraron hallazgos bloqueantes ni importantes.

### Observaciones y verificaciones
1. **Arquitectura y capas**: La separación por capas se mantiene estricta. El puerto `RepositorioPromociones` pertenece a `domain.port` y no tiene acoplamiento con JPA ni Spring. El controller no implementa lógica de negocio y delega en `GestionInventario`.
2. **Patrones GoF**: El patrón Decorator utilizado para la composición de promociones y descuentos en `ServicioValorizacion` no sufre ninguna alteración regresiva.
3. **Contratos HTTP y API REST**: El endpoint de borrado `DELETE /api/productos/{id}/promociones/{promocionId}` responde con `204 No Content` conforme a la semántica estándar, y `404 Not Found` ante promociones inexistentes o inconsistencias de producto.
4. **Design System COTO**: En `frontend/src/pages/Discounts.tsx` se utilizan exclusivamente variables de tokens (`var(--space-*)`, `var(--primary-*)`, `var(--neutral-*)`) y componentes atómicos reutilizables (`Button`, `Field`, `Empty`, `Badge`, `PageHeading`, `Confirmation`).
5. **Simplicidad**: Se reutilizó de manera óptima la lógica preexistente de formularios modales de catálogo sin duplicar contratos de mutación.

