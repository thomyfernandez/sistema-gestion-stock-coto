# Implementación — Descuentos y Promociones

## Archivos modificados y nuevos

| Archivo | Cambio |
|---|---|
| `backend/src/main/java/ar/com/coto/stock/domain/port/RepositorioPromociones.java` | Agregado método `void eliminar(long id)` al puerto del dominio. |
| `backend/src/main/java/ar/com/coto/stock/repository/RepositorioPromocionesJpa.java` | Implementado `eliminar(long id)` con `em.remove` y verificación de `EntidadNoEncontradaException("Promoción", id)`. |
| `backend/src/main/java/ar/com/coto/stock/service/GestionInventario.java` | Agregado `void eliminarPromocion(int productoId, long promocionId)` a la interfaz de aplicación. |
| `backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java` | Implementado `eliminarPromocion`: valida stock, valida pertenencia con `consultarPromocion` y delega en el repositorio de promociones de forma transaccional. |
| `backend/src/main/java/ar/com/coto/stock/controller/ProductoController.java` | Agregado endpoint `@DeleteMapping("/{id}/promociones/{promocionId}")` retornando 204 No Content. |
| `backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java` | Agregada prueba `@Test void promocionesSePuedenEliminar()` cubriendo creación, DELETE 204, verificación posterior de 404 y DELETE sobre ID inexistente arrojando 404. |
| `frontend/src/api.ts` | Agregada función `eliminarPromocion: (id: number, promocionId: number) => request<void>(...)`. |
| `frontend/src/pages/Discounts.tsx` | Creada la página completa de Descuentos con selección de artículo, tabla de promociones acumulables con switches de activación y badges semánticos, modal de confirmación `danger` para eliminar, modal `PromotionForm` dinámico y panel de simulación de precio `PriceSimulation`. |
| `frontend/src/App.tsx` | Registrada la nueva vista `descuentos` con ícono `Tag` en navegación y routing. |

## Decisiones tomadas

1. **Reutilización del diseño modular**: Se reutilizó `PromotionForm` de `CatalogForms.tsx` que ya contemplaba los 3 tipos de promoción (`DESCUENTO`, `DOS_POR_UNO`, `MEDIO_PAGO`) y las validaciones de negocio.
2. **Eliminación segura**: La eliminación se realiza mediante modal `Confirmation` con estilo destructivo (`danger`) y mensaje explicativo del impacto, impidiendo borrados accidentales.
3. **Simulación reactiva**: Se integró el simulador de cálculo de precios (`PriceSimulation`) alimentado por `api.precio(productId, cantidad, medioPago)`, permitiendo verificar en vivo el impacto del orden y acumulación de los beneficios.

## Desvíos respecto del plan o diseño

Ninguno. Se cumplieron todos los puntos especificados en `plan.md` y `diseno.md`.

## Verificación

- `frontend`: `npm run build` ejecutado exitosamente con 0 errores TypeScript y bundle Vite generado.
- `backend`: contrato Java compilable e integrado; prueba REST agregada en `ApiRestTest.java`.

