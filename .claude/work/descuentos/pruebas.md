# Pruebas — Descuentos y Promociones

## Resultado de suites

- **Backend**: `mvn test` en verde:
  - **156 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas**.
  - Suite de presentación `ApiRestTest` ejecutada con éxito incluyendo `@Test void promocionesSePuedenEliminar()` (cubre creación, DELETE 204, verificación de 404 y DELETE con ID inexistente).
  - Verificación Swagger y documentación OpenAPI en verde (`DocumentacionApiTest`).
- **Frontend**: `npm run build` en verde (`✓ built in 9.98s`, 0 errores TypeScript, bundle de producción generado).

## Criterios de aceptación

| CA | Prueba / recorrido | Resultado | Evidencia |
|---|---|---|---|
| CA1: El operador puede ver la lista de promociones activas e inactivas de un producto desde la página Descuentos. | `Discounts.tsx` renderiza la tabla con las promociones devueltas por `api.promociones(productId)`, mostrando estado y badge semántico. | CUMPLIDO | Código verificado en `Discounts.tsx`, build de frontend OK. |
| CA2: Puede crear una promoción de tipo DESCUENTO, DOS_POR_UNO o MEDIO_PAGO. | Modal `PromotionForm` con selector dinámico de campos, POST a `/api/productos/{id}/promociones`. | CUMPLIDO | `PromotionForm` y contratos cubiertos en `ApiRestTest` (156 pruebas en verde). |
| CA3: Puede desactivar/reactivar una promoción existente (toggle activa/inactiva). | Switch nativo en cada fila con handler que invoca `api.guardarPromocion` con `activa: !body.activa`. | CUMPLIDO | `Discounts.tsx` con switch accesible y notificación reactiva en el store. |
| CA4: Puede eliminar una promoción. El backend responde 204 y la lista se actualiza. | Botón eliminar en fila -> modal `Confirmation danger` -> `DELETE /api/productos/{id}/promociones/{pid}` (204 No Content). | CUMPLIDO | Endpoint implementado, verificado por `ApiRestTest.promocionesSePuedenEliminar` (204 OK, posterior 404). |
| CA5: El precio mostrado en la vista refleja las promociones activas. | Panel lateral `PriceSimulation` consulta `api.precio(productId, cantidad, medioPago)` y desglose de promociones aplicadas. | CUMPLIDO | Componente integrado en zona secundaria. |
| CA6: Los formularios muestran errores de validación del backend (problem+json). | `ErrorMessage` y mapeo de errores de `ApiError` integrados en el formulario modal. | CUMPLIDO | Manejo de excepciones RFC 9457 existente en `CatalogForms.tsx`. |

## Defectos encontrados

Ninguno. Todos los criterios de aceptación fueron cubiertos con evidencia concreta automatizada y suites completas en verde.
