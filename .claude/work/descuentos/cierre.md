# Cierre — Descuentos y Promociones

## Resumen de Criterios de Aceptación

| CA | Estado | Evidencia |
|---|---|---|
| CA1: El operador puede ver la lista de promociones activas e inactivas de un producto. | Cumplido | `Discounts.tsx` implementa tabla con listado reactivo y badge semántico. |
| CA2: Puede crear una promoción de tipo DESCUENTO, DOS_POR_UNO o MEDIO_PAGO. | Cumplido | Modal dinámico `PromotionForm` conectado a la API de promociones. |
| CA3: Puede desactivar/reactivar una promoción existente. | Cumplido | Switch interactivo por fila conectado a PUT en `api.guardarPromocion`. |
| CA4: Puede eliminar una promoción con respuesta 204 y actualización de lista. | Cumplido | Endpoint DELETE en controller, método transaccional y modal Confirmation. |
| CA5: El precio mostrado en la vista refleja las promociones activas. | Cumplido | Panel lateral de simulación calculando ahorro y total en tiempo real. |
| CA6: Los formularios muestran errores de validación del backend (problem+json). | Cumplido | Mapeo de errores RFC 9457 en inputs y mensajes de error accesibles. |

## Hallazgos del reviewer
Resueltos sin observaciones bloqueantes. Veredicto del reviewer: APROBADO.

## Veredicto final del líder
**APROBADO**

La tarea está finalizada y lista para su integración.

