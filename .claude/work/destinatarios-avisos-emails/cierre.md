# Informe de Cierre: Destinatarios de Avisos como Listas de Correos Electrónicos

> **Módulo:** Configuración de la Sucursal & Compras y Abastecimiento  
> **Slug de trabajo:** `destinatarios-avisos-emails`  
> **Responsable:** Líder Técnico COTO Backoffice  
> **Fecha:** 3 de noviembre de 2026  
> **Estado:** **APROBADO**

---

## 1. Contexto y Objetivos Cumplidos

El requerimiento consistió en desacoplar la asignación individual de contactos y canales por artículo en el modal de Compras y Reposición hacia una administración centralizada y jerárquica de listas de correos electrónicos en Configuración.

El sistema implementó con éxito:
1. Una nueva sección en **Configuración → Destinatarios de avisos** para gestionar listas de correos generales de la sucursal y listas especializadas por categoría de producto.
2. Resolución jerárquica transparente en la emisión de alertas de stock bajo: si la categoría posee emails configurados, se envían a esa lista; de lo contrario (o ante productos sin categoría), se canalizan a la lista general de la sucursal.
3. Encolado múltiple de eventos de alerta en `evento_integracion` con canal `EMAIL` y claves únicas de outbox por destinatario (`clave + ":alerta:" + productoId + ":" + email`), preservando idempotencia y detectores de ciclo de vida del episodio.
4. Simplificación del modal de política de reposición en Compras (eliminación de contactos y canales particulares, incorporando tarjeta contextual de resolución de correos y enlaces al sector de configuración).
5. Script de migración idempotente `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql` para PostgreSQL y H2 con índices únicos parciales.

---

## 2. Tabla de Criterios de Aceptación

| Criterio | Descripción | Estado | Evidencia técnica |
| --- | --- | --- | --- |
| **CA1** | Gestión y validación de lista general en Configuración (formato email, sin duplicados, persistida y expuesta vía API REST). | **Cumplido** | • Validación RFC (`^[^@\s]+@[^@\s]+\.[^@\s]+$`) y normalización en [`DestinatarioAviso.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/model/DestinatarioAviso.java).<br>• Desduplicación con `LinkedHashSet` en [`RepositorioDestinatariosJpa.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/RepositorioDestinatariosJpa.java).<br>• Índice único parcial `uq_destinatario_general` (`categoria_id IS NULL`).<br>• Endpoints `GET` y `PUT /api/configuracion/destinatarios/general` con RFC 9457.<br>• Pruebas: [`DestinatarioAvisoTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/DestinatarioAvisoTest.java), [`ServicioDestinatariosTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java), [`ApiRestTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java). |
| **CA2** | Gestión de listas por categoría en `destinatario_aviso`, validando unicidad, existencia de categoría y herencia. | **Cumplido** | • Índice único parcial `uq_destinatario_categoria` (`categoria_id, email`).<br>• [`ServicioDestinatarios.guardarPorCategoria`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/ServicioDestinatarios.java) valida categoría existente (HTTP 404 si no existe) y limpia registros al enviar lista vacía para restablecer herencia.<br>• Pruebas: [`ServicioDestinatariosTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java), [`SchemaTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java), [`ApiRestTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java). |
| **CA3** | Modal de Política de Reposición en Compras simplificado: solo parámetros de reposición y umbral, sin contacto ni canal. | **Cumplido** | • Componente [`CatalogForms.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/components/CatalogForms.tsx) refactorizado (DEF-01 resuelto).<br>• Eliminados selectores de contacto y canal.<br>• Incorporada tarjeta `.notice-resolution-card` con [`resolverDestinatariosAviso`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/notifications.ts) y enlace directo a `#/configuracion/destinatarios`. |
| **CA4** | Endpoint `PUT /api/productos/{id}/politica-reposicion` persiste con HTTP 200 sin exigir contacto ni canal. | **Cumplido** | • [`Solicitudes.Politica`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/dto/Solicitudes.java) y entidad libres de campos de contacto.<br>• Se descarta constraint `ck_aviso_particular`. Campos obsoletos recibidos de clientes antiguos se ignoran de forma transparente.<br>• Pruebas: [`ApiRestTest.politicaReposicionSeActualizaSinCamposDeAvisoParticular()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java). |
| **CA5** | Alerta múltiple por categoría: encola en `evento_integracion` un evento por cada email con canal `EMAIL` y clave única. | **Cumplido** | • [`GestorInventario.guardarAlertas`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java) delega en `resolverDestinatarios(id)` y encola eventos con clave `clave + ":alerta:" + id + ":" + email`.<br>• Pruebas: [`AplicacionIntegracionTest.alertaPorCategoriaEncolaEventoPorCadaDestinatarioConClaveUnica()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java). |
| **CA6** | Fallback jerárquico a lista general si la categoría no tiene emails o el artículo no tiene categoría asignada. | **Cumplido** | • Lógica en [`ServicioDestinatarios.resolverDestinatarios`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/ServicioDestinatarios.java). Si la lista general también está vacía, no emite eventos y marca `alerta_activa = true`.<br>• Pruebas: [`ServicioDestinatariosTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java) y [`AplicacionIntegracionTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java). |
| **CA7** | Ciclo de vida y rearmado: sin duplicados en el mismo episodio de descenso; emite nuevo lote al superar umbral y volver a caer. | **Cumplido** | • [`AlertaStockBajo.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/observer/AlertaStockBajo.java) gestiona el mapa `alertados` y rearmado al superar el umbral.<br>• Pruebas: [`ObserverTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/ObserverTest.java) y [`AplicacionIntegracionTest.alertaNoDuplicaEnMismoEpisodioYGeneraNuevoLoteAlRearmar()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java). |
| **CA8** | Script idempotente `upgrade-destinatarios-avisos.sql` migra datos, limpia constraints y valida con `UpgradeEtapaDosTest`. | **Cumplido** | • Script DDL/DML idempotente con `NOT EXISTS`, `IF NOT EXISTS` y eliminación de constraint legada.<br>• Pruebas: [`UpgradeEtapaDosTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java) con doble ejecución secuencial en verde y [`SchemaTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java). |
| **CA9** | Interfaz conforme al Design System COTO (tokens `var(--...)`, grilla de 8 pt, proporción 60/30/10, accesibilidad e íconos `lucide-react`). | **Cumplido** | • Átomos CSS en [`components.css`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/styles/components.css) (`.email-chip`, `.email-input-group`, `.notice-resolution-card`).<br>• Accesibilidad de chips con foco y `aria-label`.<br>• Columna "Avisar a" en [`Purchases.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/Purchases.tsx) con `tabIndex={0}`, `aria-label` y tooltip.<br>• Documentación en [`docs/design-system/README.md`](file:///c:/Users/juanp/sistema-gestion-stock-coto/docs/design-system/README.md). |

---

## 3. Estado de Hallazgos y Auditoría Técnica

Todos los hallazgos reportados durante el ciclo de revisión y auditoría han sido **100 % resueltos y verificados**:

| # | Severidad | Hallazgo original | Estado | Resolución aplicada |
|---|---|---|---|---|
| 1 | **Bloqueante** | `PolicyForm` en `CatalogForms.tsx` no se había actualizado, retenía campos legados y lanzaba error en runtime (`ReferenceError: destinoAviso is not defined`). | **RESUELTO** | Se refactorizó `PolicyForm` en `CatalogForms.tsx`, quitando `contactoId`/`canalAlerta` y añadiendo `.notice-resolution-card` mediante `resolverDestinatariosAviso(data, product)` conforme a `diseno.md`. |
| 2 | **Importante** | Archivos `schema.sql` y `UpgradeEtapaDosTest.java` contenían caracteres nulos/BOM tras edición inicial. | **RESUELTO** | Re-grabados y normalizados en UTF-8 estándar sin BOM. Leídos y verificados limpiamente en Git y compilación. |
| 3 | **Importante** | Invocación residual a `d.contactos.resolver(...)` en `GestorInventario.configurarPolitica(...)`. | **RESUELTO** | Se eliminó el llamado obsoleto, preservando exclusivamente la lógica de inventario, validación de `StockSimple` y persistencia. |
| 4 | **Menor** | `App.tsx` no incluía el subtítulo para la solapa `destinatarios` ni navegación por defecto. | **RESUELTO** | Se registró la clave `'destinatarios': 'Listas de correos para alertas de stock bajo.'` en `subtitles` y se definió como tab activa inicial. |
| 5 | **Menor** | Estado local desincronizado en `NoticeRecipients.tsx` al actualizarse el store global. | **RESUELTO** | Se integraron `useEffect` en `GeneralRecipientsCard` y `CategoryRecipientsCard` dependientes de `data?.destinatarios`. |
| 6 | **Mejora DS** | Accesibilidad por teclado en la columna "Avisar a" de la grilla de reposición. | **RESUELTO** | Se configuró `tabIndex={0}`, `aria-label` descriptivo y tooltip con cursor help en `Purchases.tsx`. |

---

## 4. Veredicto Final

# **APROBADO**

- **Criterios de Aceptación:** 9 de 9 cumplidos (**100 %**).
- **Hallazgos del Reviewer:** 0 pendientes (**6/6 resueltos**).
- **Suites de Pruebas:** Pruebas unitarias, de integración, esquema y regresión documentadas en verde en [`pruebas.md`](file:///c:/Users/juanp/sistema-gestion-stock-coto/.claude/work/destinatarios-avisos-emails/pruebas.md).
- **Reglas del Equipo:** No se realiza commit ni push automático, preservando el estado del repositorio para revisión final del usuario.
