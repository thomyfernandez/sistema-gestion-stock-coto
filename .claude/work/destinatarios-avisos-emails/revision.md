# Informe de Revisión de Código: Destinatarios de avisos como listas de emails (Vuelta 2)

> **Slug de trabajo:** `destinatarios-avisos-emails`  
> **Revisor:** Reviewer independiente COTO Backoffice  
> **Fecha:** 3 de noviembre de 2026  

---

## Veredicto
**APROBADO**

---

## Verificación de Hallazgos

| # | Severidad | Archivo | Estado | Detalle de la verificación |
|---|---|---|---|---|
| 1 | **Bloqueante** | `frontend/src/components/CatalogForms.tsx` | **RESUELTO** | `PolicyForm` fue refactorizado por completo: se eliminaron los selectores de contacto particular y canal, se integró la tarjeta `.notice-resolution-card` consumiendo `resolverDestinatariosAviso(data, product)` y se añadió el enlace contextual a `#/configuracion/destinatarios`. |
| 2 | **Importante** | `backend/src/main/resources/schema.sql` y `backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java` | **RESUELTO** | Ambos archivos fueron re-grabados y normalizados en codificación UTF-8 estricta sin BOM ni caracteres de control / bytes nulos. Se leen como texto plano unificado y se integran limpiamente en el control de versiones y suites de prueba. |
| 3 | **Importante** | `backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java` | **RESUELTO** | Se removió la invocación residual a `d.contactos.resolver(...)` en `configurarPolitica(...)`, preservando el bloqueo de inventario transaccional y la validación de `StockSimple`. |
| 4 | **Menor** | `frontend/src/App.tsx` | **RESUELTO** | Se agregó la clave `'destinatarios': 'Listas de correos para alertas de stock bajo.'` en el diccionario `subtitles` y se definió como solapa inicial al navegar a `#configuracion`. |
| 5 | **Menor** | `frontend/src/pages/NoticeRecipients.tsx` | **RESUELTO** | Se agregaron hooks `useEffect` en `GeneralRecipientsCard` y `CategoryRecipientsCard` para sincronizar el estado reactivo local cada vez que se actualiza `data.destinatarios` desde el store global. |

*Mejora adicional verificada:* En `frontend/src/pages/Purchases.tsx`, la columna "Avisar a" incorpora `tabIndex={0}` y `aria-label` para asegurar accesibilidad y lectura del tooltip mediante navegación por teclado.

---

## Hallazgos
No se registran hallazgos pendientes.
