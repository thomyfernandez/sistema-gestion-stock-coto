# Destinatarios de avisos como listas de emails

## Contexto
Actualmente, el modal de Compras → Reposición acopla los parámetros de reabastecimiento con la definición de un contacto y canal particular por artículo. Se solicita desacoplar dicha responsabilidad hacia una nueva sección en Configuración que gestione listas de correos electrónicos (una lista general para la sucursal y listas específicas por categoría de producto). Al descender el stock por debajo del umbral, el sistema enviará un correo a cada dirección aplicable mediante resolución jerárquica (categoría si tiene correos, o lista general).
Supuestos explícitos:
- Los canales SMS y WhatsApp quedan en segundo plano: las alertas automáticas de reposición se centran en el canal de correo electrónico (EMAIL).
- La resolución de destinatarios es jerárquica: si la categoría del artículo tiene direcciones de email configuradas, se envían a esa lista; si la categoría no tiene correos o el artículo no tiene categoría, se envían a la lista general de la sucursal.
- Si tanto la lista de categoría como la lista general estuvieran vacías, no se encolan eventos de notificación pero el episodio de stock bajo se registra con normalidad.
- El contrato de la política de reposición queda enfocado exclusivamente en las reglas de stock (tipo, umbrales, stock objetivo, factores de demanda/temporada), eliminando contacto/canal del modal y de la API.
- Se debe asegurar compatibilidad con bases de datos existentes mediante un script de migración idempotente `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql`, actualizando además `schema.sql` y `data.sql`.

## Alcance
- Incluye:
  - Nueva tabla `destinatario_aviso` en el esquema relacional con soporte para listas generales (`categoria_id IS NULL`) y listas asociadas a categorías (`categoria_id` FK a `categoria`), con validación de formato e índices únicos parciales.
  - Script de migración `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql` idempotente para PostgreSQL y H2, migrando los correos actuales de `configuracion_avisos` y de contactos vinculados a categorías.
  - Actualización de `backend/src/main/resources/schema.sql` y `backend/src/main/resources/data.sql`.
  - Refactor del modelo de dominio `PoliticaReposicion` para eliminar `contactoId` y `canalAlerta`, conservando exclusivamente parámetros de reposición y `umbralAlerta`.
  - Nuevo modelo y puerto de dominio (`DestinatarioAviso`, `RepositorioDestinatarios`) y su implementación JPA (`RepositorioDestinatariosJpa`, `DestinatarioAvisoEntity`).
  - Servicio de dominio y aplicación para gestión y resolución jerárquica de destinatarios (`GestionDestinatarios`, `ServicioDestinatarios`).
  - Adaptación de `GestorInventario.guardarAlertas` para encolar un `EventoIntegracion` de tipo `ALERTA` (canal `EMAIL`) por cada destinatario resuelto para el artículo bajo umbral, garantizando claves de idempotencia únicas en la outbox.
  - Endpoints REST dedicados en `DestinatarioController` (`@Tag("Destinatarios de avisos")`): `GET /api/configuracion/destinatarios`, `PUT /api/configuracion/destinatarios/general` y `PUT /api/configuracion/destinatarios/categorias/{categoriaId}` con sus DTOs validados en `Solicitudes` y `Respuestas`.
  - Simplificación del DTO y controlador de política de reposición (`Solicitudes.Politica`, `Respuestas.Politica`, `ProductoController`), quitando campos de contacto particular (o manteniéndolos como opcionales/ignorados por compatibilidad).
  - Nueva interfaz de usuario en **Configuración → Destinatarios de avisos** para administrar listas de emails (general y por categoría) usando componentes y tokens del Design System COTO.
  - Rediseño del modal **Compras → Reposición → Política**: supresión de selectores de contacto y canal, manteniendo solo campos de política y umbral.
  - Actualización de la columna "Avisar a" en la tabla de políticas de Compras para mostrar el resumen de destinatarios resueltos (categoría o general).
  - Actualizaciones en `types.ts`, `api.ts`, `store.tsx` y `notifications.ts`.
  - Pruebas automatizadas en backend: `ObserverTest`, `ServiciosTest`, `AplicacionIntegracionTest`, `ApiRestTest`, `UpgradeEtapaDosTest` y `SchemaTest`.
- No incluye:
  - Eliminación de la tabla `proveedor` (agenda de contactos de proveedores), la cual se conserva para compatibilidad histórica.
  - Envío real SMTP de correos (se mantiene la simulación transaccional desacoplada con `AdaptadorProveedor` / `ApiProveedorExterno`).
  - Listas de distribución para SMS o WhatsApp.

## Criterios de aceptación
- [ ] CA1 — Dado un usuario en Configuración → Destinatarios de avisos, cuando agrega, edita o elimina direcciones de email en la lista general de la sucursal, entonces las direcciones quedan validadas en formato de correo electrónico, persistidas sin duplicados y expuestas a través de la API REST.
- [ ] CA2 — Dado un usuario en Configuración → Destinatarios de avisos, cuando define o modifica la lista de emails para una categoría específica, entonces la lista se guarda vinculada a dicha categoría en `destinatario_aviso`, validando unicidad y formato.
- [ ] CA3 — Dado un operador en Compras → Reposición, cuando abre el modal "Política de reposición" de un artículo simple, entonces solo visualiza y edita los parámetros de abastecimiento (tipo, punto de pedido, stock objetivo, demanda diaria, días de cobertura, factor estacional) y el umbral de alerta, sin campos de contacto ni de canal.
- [ ] CA4 — Dado el endpoint HTTP `PUT /api/productos/{id}/politica-reposicion`, cuando se envía el cuerpo con únicamente los parámetros de reposición y umbral de alerta (sin contactoId ni canalAlerta), entonces la política se persiste con éxito (HTTP 200) y se descartan las restricciones de aviso particular.
- [ ] CA5 — Dado un artículo cuya categoría posee 2 o más emails configurados, cuando su stock disponible cae por debajo del umbral de alerta, entonces `AlertaStockBajo` notifica al gestor y este encola en `evento_integracion` un evento de alerta por cada email de la categoría con canal `EMAIL` y clave de outbox única.
- [ ] CA6 — Dado un artículo cuya categoría no tiene emails cargados (o que no pertenece a ninguna categoría), cuando su stock disponible cae por debajo del umbral, entonces el sistema resuelve la lista general de la sucursal y encola un evento de alerta por cada dirección de la lista general.
- [ ] CA7 — Dado un producto en estado de stock bajo que ya generó alertas en su outbox, cuando se registran movimientos sucesivos que disminuyen aún más sus existencias dentro del mismo episodio, entonces no se duplican las alertas encoladas; y cuando el stock supera el umbral y vuelve a caer, se rearma y emite un nuevo lote de alertas para cada destinatario configurado.
- [ ] CA8 — Dada una base de datos inicializada hasta `upgrade-categorias.sql`, cuando se ejecuta el script `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql`, entonces se crea la tabla `destinatario_aviso`, se transfiere el email existente de `configuracion_avisos` a la lista general, se eliminan restricciones obsoletas en `politica_reposicion` y la suite `UpgradeEtapaDosTest` finaliza en verde de manera idempotente.
- [ ] CA9 — Dado un usuario navegando en el backoffice, cuando interactúa con las vistas de Destinatarios de avisos y el modal de reposición, entonces todos los componentes visuales respetan el Design System COTO (tokens CSS `var(--...)`, espaciados múltiplos de 8 pt, proporción 60/30/10 e íconos `lucide-react`).

## Impacto
| Capa | Archivos | Cambio |
| --- | --- | --- |
| Base de datos / Migraciones | `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql` | Nuevo script SQL idempotente: crea `destinatario_aviso`, migra emails existentes y limpia constraints obsoletas en `politica_reposicion`. |
| Base de datos / Migraciones | `backend/src/main/resources/schema.sql` | Incorpora la definición de `destinatario_aviso` y simplifica la tabla `politica_reposicion` (elimina columnas de aviso particular). |
| Base de datos / Migraciones | `backend/src/main/resources/data.sql` | Inserta datos de muestra para la lista general y listas por categoría en `destinatario_aviso`; adapta inserts de `politica_reposicion`. |
| Dominio | `backend/src/main/java/ar/com/coto/stock/domain/reposicion/PoliticaReposicion.java` | Remueve atributos y validaciones de `contactoId`, `canalAlerta` y `destinoAlerta`. |
| Dominio | `backend/src/main/java/ar/com/coto/stock/domain/observer/AlertaStockBajo.java` | Simplifica el modelo de alerta para desacoplar el destino único particular, manteniendo constructores retrocompatibles. |
| Dominio | `backend/src/main/java/ar/com/coto/stock/domain/model/DestinatarioAviso.java` | Nuevo record de dominio para destinatarios de avisos por correo. |
| Dominio / Puertos | `backend/src/main/java/ar/com/coto/stock/domain/port/RepositorioDestinatarios.java` | Nuevo puerto para persistencia y consulta de listas de emails generales y por categoría. |
| Persistencia | `backend/src/main/java/ar/com/coto/stock/repository/entity/DestinatarioAvisoEntity.java` | Entidad JPA para la tabla `destinatario_aviso`. |
| Persistencia | `backend/src/main/java/ar/com/coto/stock/repository/entity/PoliticaReposicionEntity.java` | Limpieza de mapeo JPA de columnas de contacto y canal particular. |
| Persistencia | `backend/src/main/java/ar/com/coto/stock/repository/RepositorioDestinatariosJpa.java` | Implementación del puerto `RepositorioDestinatarios` sobre JPA. |
| Servicios | `backend/src/main/java/ar/com/coto/stock/service/GestionDestinatarios.java`, `ServicioDestinatarios.java` | Interfaz y servicio para gestión y resolución jerárquica de destinatarios de stock bajo. |
| Servicios | `backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java` | Integra `GestionDestinatarios` en dependencias del Singleton y encola un `EventoIntegracion` por cada email resuelto en `guardarAlertas`. |
| Controladores / DTOs | `backend/src/main/java/ar/com/coto/stock/controller/DestinatarioController.java` | Nuevo controlador REST para consultar y actualizar listas de destinatarios (`@Tag(name = "Destinatarios de avisos")`). |
| Controladores / DTOs | `backend/src/main/java/ar/com/coto/stock/dto/Solicitudes.java`, `Respuestas.java`, `MapeadorDto.java` | DTOs para listas de correos; remoción de campos de contacto en `Politica`. |
| Frontend / API y Estado | `frontend/src/types.ts`, `frontend/src/api.ts`, `frontend/src/store.tsx` | Contratos de tipos para listas de emails, llamadas API y sincronización de estado global en el snapshot. |
| Frontend / Vistas y Componentes | `frontend/src/pages/NoticeRecipients.tsx`, `frontend/src/pages/Settings.tsx` | Nueva vista/tab para gestión de listas de emails en Configuración. |
| Frontend / Vistas y Componentes | `frontend/src/components/CatalogForms.tsx` | Simplificación de `PolicyForm`: eliminación de campos de contacto y canal. |
| Frontend / Vistas y Componentes | `frontend/src/pages/Purchases.tsx`, `frontend/src/notifications.ts` | Actualización de la columna "Avisar a" en la grilla de políticas para indicar destinatarios resueltos. |
| Pruebas | `backend/src/test/...` | Actualizaciones en `ObserverTest`, `ServiciosTest`, `AplicacionIntegracionTest`, `ApiRestTest`, `UpgradeEtapaDosTest` y `SchemaTest`. |

## Tareas
1. [disenador-ux-ui] Diseñar el flujo y las pantallas para la gestión de listas de emails en **Configuración → Destinatarios de avisos** (panel de lista general y acordeón/secciones por categoría con estados vacíos, chips de email y validación), el modal simplificado de política en Compras y la columna "Avisar a" de la tabla de reposición. Redactar especificación en `.claude/work/destinatarios-avisos-emails/diseno.md`.
2. [implementador] Crear el script de migración `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql` idempotente y actualizar `schema.sql` y `data.sql`.
3. [implementador] Implementar el modelo de dominio `DestinatarioAviso`, el puerto `RepositorioDestinatarios`, `DestinatarioAvisoEntity` y `RepositorioDestinatariosJpa`.
4. [implementador] Implementar `GestionDestinatarios` y `ServicioDestinatarios` con la lógica de resolución jerárquica (correos de la categoría si existen; en su defecto, lista general).
5. [implementador] Refactorizar `PoliticaReposicion` y `PoliticaReposicionEntity` para remover `contactoId` y `canalAlerta`. Adaptar `AlertaStockBajo`.
6. [implementador] Actualizar `GestorInventario`: incorporar `GestionDestinatarios` y actualizar `guardarAlertas` para generar un evento de integración por cada dirección de email resuelta con clave única.
7. [implementador] Implementar `DestinatarioController` con DTOs en `Solicitudes`, `Respuestas` y `MapeadorDto`, documentado con springdoc OpenAPI (`@Tag`).
8. [implementador] Adaptar contratos y estado del frontend: `types.ts`, `api.ts`, `store.tsx` y `notifications.ts`.
9. [implementador] Implementar la interfaz de **Configuración → Destinatarios de avisos** en `NoticeRecipients.tsx` (o `Settings.tsx`), simplificar `PolicyForm` en `CatalogForms.tsx` y ajustar la grilla de políticas en `Purchases.tsx`, siguiendo el Design System y `diseno.md`.
10. [tester] Actualizar `UpgradeEtapaDosTest` y `SchemaTest` verificando la ejecución sin errores de la cadena completa de migraciones y la persistencia de la nueva tabla.
11. [tester] Implementar pruebas unitarias y de integración para `AlertaStockBajo`, `ServicioDestinatarios`, `GestorInventario` y `ApiRestTest` validando el encolado de múltiples alertas, la resolución jerárquica y los endpoints REST.
12. [tester] Ejecutar la suite completa (`mvn test`, build de frontend), auditar criterios de aceptación y redactar `.claude/work/destinatarios-avisos-emails/pruebas.md`.

## Riesgos
- Invariantes y unicidad en SQL: Las restricciones de unicidad de emails deben ser compatibles tanto en PostgreSQL como en H2 (utilizar índices únicos parciales con cláusula `WHERE` para diferenciar la lista general de las categorías).
- Claves de Outbox en integración: Al emitirse múltiples alertas por episodio para un mismo artículo, cada `EventoIntegracion` debe contar con una clave única en la base (ej. `clave + ":alerta:" + stockId + ":" + email`) para evitar violaciones de la restricción `UNIQUE (clave)`.
- Estado del episodio de stock bajo: `alerta_activa` debe continuar asociándose al artículo en `politica_reposicion` para evitar re-envíos espurios en transacciones sucesivas del mismo episodio.
- Retrocompatibilidad en API REST: Al actualizar `Solicitudes.Politica`, se debe garantizar que clientes que eventualmente envíen campos en desuso no fallen por atributos no reconocidos.

## Preguntas abiertas
- *Comportamiento ante listas vacías*: Se establece que si un artículo no tiene emails en su categoría y la lista general de la sucursal se encuentra vacía, no se generarán eventos de integración en la outbox pero el estado de alerta activa del producto se registrará normalmente.
- *Estrategias de SMS y WhatsApp*: Se conservan las clases `SmsNotificador` y `WhatsAppNotificador` en la capa de adaptadores para proteger la integridad del patrón Strategy GoF y las pruebas unitarias existentes, pero las alertas de inventario automáticas pasan a canalizar exclusivamente vía correo electrónico.
