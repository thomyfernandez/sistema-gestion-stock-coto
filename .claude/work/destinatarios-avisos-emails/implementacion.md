# Implementación: Destinatarios de avisos como listas de correos electrónicos

> **Módulo:** Configuración de la Sucursal & Compras y Abastecimiento  
> **Slug de trabajo:** `destinatarios-avisos-emails`  
> **Responsable:** Implementador COTO Backoffice  
> **Fecha:** 3 de noviembre de 2026 (Actualizado: Vuelta 1 de corrección)  
> **Estado:** Correcciones de Vuelta 1 completadas — Listo para re-verificación  

---

## 1. Archivos Creados y Modificados

### 1.1 Base de datos y migraciones
- [`backend/src/main/resources/db/upgrade-destinatarios-avisos.sql`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/resources/db/upgrade-destinatarios-avisos.sql):
  - Nuevo script SQL idempotente para PostgreSQL y H2 en modo PostgreSQL.
  - Crea la tabla `destinatario_aviso` con índices únicos parciales (`categoria_id IS NULL` para lista general y `categoria_id IS NOT NULL` para categorías).
  - Elimina el constraint `ck_aviso_particular` y quita la obligatoriedad de `destino_alerta` en `politica_reposicion`.
  - Migra de forma idempotente (`NOT EXISTS`) el correo general existente desde `configuracion_avisos` y los correos de contactos vinculados a categorías desde `categoria` + `proveedor`.
- [`backend/src/main/resources/schema.sql`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/resources/schema.sql):
  - Incorpora la definición canónica de `destinatario_aviso` e índices correspondientes.
  - Simplifica la tabla `politica_reposicion` eliminando `destino_alerta`, `proveedor_id`, `canal_alerta` y el check `ck_aviso_particular`.
- [`backend/src/main/resources/data.sql`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/resources/data.sql):
  - Actualiza las filas iniciales de `politica_reposicion` sin columnas de contacto particular.
  - Inserta destinatarios de ejemplo en `destinatario_aviso`: lista general (`compras@coto.example`, `sucursal42.stock@coto.com.ar`) y lista para la categoría Frescos (`frescos@coto.example`, `lacteos.sucursal@coto.com.ar`).

### 1.2 Capa de Dominio y Puertos
- [`backend/src/main/java/ar/com/coto/stock/domain/model/DestinatarioAviso.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/model/DestinatarioAviso.java):
  - Record inmutable de dominio con validación de identificadores positivos, normalización (strip + lowercase) y validación estricta de formato de correo (`^[^@\s]+@[^@\s]+\.[^@\s]+$`).
- [`backend/src/main/java/ar/com/coto/stock/domain/reposicion/PoliticaReposicion.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/reposicion/PoliticaReposicion.java):
  - Refactorizado para tener como componentes principales exclusivamente los parámetros de reposición y `umbralAlerta`.
  - Mantiene constructores y métodos accesores sobrecargados (`destinoAlerta()`, `contactoId()`, `canalAlerta()`) para retrocompatibilidad absoluta con pruebas existentes.
- [`backend/src/main/java/ar/com/coto/stock/domain/observer/AlertaStockBajo.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/observer/AlertaStockBajo.java):
  - Incorpora constructor sobrecargado `AlertaStockBajo(int umbral, EmisorAlertas emisor)` desacoplado del destino físico particular.
- [`backend/src/main/java/ar/com/coto/stock/domain/port/RepositorioDestinatarios.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/port/RepositorioDestinatarios.java):
  - Puerto de dominio puro (sin Spring, JPA ni SQL) para listar generales, listar por categoría, listar todos y guardar listas.

### 1.3 Capa de Persistencia
- [`backend/src/main/java/ar/com/coto/stock/repository/entity/DestinatarioAvisoEntity.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/entity/DestinatarioAvisoEntity.java):
  - Entidad JPA para la tabla `destinatario_aviso`.
- [`backend/src/main/java/ar/com/coto/stock/repository/entity/PoliticaEntity.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/entity/PoliticaEntity.java) y [`PoliticaReposicionEntity.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/entity/PoliticaReposicionEntity.java):
  - Limpieza de columnas en desuso (`destinoAlerta`, `contactoId`, `canalAlerta`) y clase de compatibilidad.
- [`backend/src/main/java/ar/com/coto/stock/repository/RepositorioDestinatariosJpa.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/RepositorioDestinatariosJpa.java):
  - Implementación sobre JPA de `RepositorioDestinatarios` con desduplicación garantizada vía `LinkedHashSet`.
- [`backend/src/main/java/ar/com/coto/stock/repository/RepositorioPoliticasJpa.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/RepositorioPoliticasJpa.java):
  - Adaptado a la entidad `PoliticaEntity` y `PoliticaReposicion` simplificadas.

### 1.4 Capa de Servicios y Singleton
- [`backend/src/main/java/ar/com/coto/stock/service/GestionDestinatarios.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/GestionDestinatarios.java) y [`ServicioDestinatarios.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/ServicioDestinatarios.java):
  - Contrato e implementación de servicio con resolución jerárquica: evalúa si la categoría del producto tiene correos propios; si tiene, devuelve esa lista; si no tiene o el producto no tiene categoría, devuelve la lista general; si ambas están vacías, lista vacía.
- [`backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java):
  - Integrado `GestionDestinatarios` en `Dependencias` del Singleton con constructor retrocompatible.
  - En `guardarAlertas`, resuelve la lista de emails mediante `d.destinatarios.resolverDestinatarios(id)` y encola un `EventoIntegracion` de tipo `ALERTA` con canal `EMAIL` y clave outbox única (`clave + ":alerta:" + id + ":" + email`) por cada destinatario.
  - En `configurarPolitica`, eliminada la invocación residual a `d.contactos.resolver(...)`.
- [`backend/src/main/java/ar/com/coto/stock/config/ConfiguracionAplicacion.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/config/ConfiguracionAplicacion.java):
  - Declaración del Bean `ServicioDestinatarios` e inyección en `GestorInventario`.

### 1.5 Controladores REST y DTOs
- [`backend/src/main/java/ar/com/coto/stock/controller/DestinatarioController.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/controller/DestinatarioController.java):
  - Nuevo controlador REST anotado con `@Tag(name = "Destinatarios de avisos")` mapeado en `/api/configuracion/destinatarios`. Endpoints: `GET /`, `PUT /general`, `PUT /categorias/{categoriaId}`.
- [`backend/src/main/java/ar/com/coto/stock/dto/Solicitudes.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/dto/Solicitudes.java):
  - Nuevo record `Destinatarios(List<@NotBlank @Email @Size(max = 200) String> emails)`.
- [`backend/src/main/java/ar/com/coto/stock/dto/Respuestas.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/dto/Respuestas.java):
  - Nuevo record `DestinatariosAvisos(List<String> general, Map<Integer, List<String>> porCategoria)` y constructor sobrecargado en `Politica`.
- [`backend/src/main/java/ar/com/coto/stock/dto/MapeadorDto.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/dto/MapeadorDto.java):
  - Adaptación de los mapeos bidireccionales de `Politica`.

### 1.6 Frontend y Design System COTO
- [`frontend/src/styles/components.css`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/styles/components.css):
  - Incorporados átomos `.email-chip`, `.email-chip-list`, `.email-chip-remove`, `.email-input-group` (con media query responsive para `< 540px`), `.message.warning`, `.accordion-trigger-heading` y `.notice-resolution-card` basados 100% en tokens COTO (`var(--neutral-*)`, `var(--space-*)`, `var(--radius-*)`).
- [`docs/design-system/README.md`](file:///c:/Users/juanp/sistema-gestion-stock-coto/docs/design-system/README.md):
  - Documentada la anatomía y uso accesible de los chips y tarjetas de resolución de avisos.
- [`frontend/src/types.ts`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/types.ts):
  - Tipos `DestinatariosAvisos`, `ActualizarDestinatariosRequest`, simplificación de `Politica` y agregado de `destinatarios` a `Snapshot`.
- [`frontend/src/notifications.ts`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/notifications.ts):
  - Implementada función `resolverDestinatariosAviso(data, product)` que calcula origen (`CATEGORIA`, `GENERAL`, `NINGUNO`), emails resueltos, nombre de categoría y texto de resumen.
- [`frontend/src/api.ts`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/api.ts):
  - Métodos API `destinatarios`, `guardarDestinatariosGeneral` y `guardarDestinatariosCategoria`.
- [`frontend/src/store.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/store.tsx):
  - Consulta paralela de `destinatarios` en `refresh()` y almacenamiento en el `Snapshot`.
- [`frontend/src/pages/NoticeRecipients.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/NoticeRecipients.tsx):
  - Vista completa con tarjeta de lista general y acordeón de categorías según `diseno.md`:
    - Sincronización reactiva de estado con `useEffect(() => { ... }, [data?.destinatarios])`.
    - Eliminados estilos inline de margen y alineación en botones, delegando en `.email-input-group`.
    - Chips con botón eliminar accesible (`aria-label`, foco visible).
    - Carga secuencial con tecla Enter en input.
    - Validación inline de formato y unicidad case-insensitive.
    - Badges de estado `Guardado` / `Sin guardar` y resolución (`propios`, `hereda`, `sin destinatarios`).
    - Acción de "Restablecer a lista general" para categorías.
- [`frontend/src/pages/Settings.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/Settings.tsx):
  - Solapa principal "Destinatarios de avisos" (`#/configuracion/destinatarios`), "Categorías" y "Agenda de proveedores".
- [`frontend/src/App.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/App.tsx):
  - Subtítulo agregado para `'destinatarios': 'Listas de correos para alertas de stock bajo.'`.
  - Navegación por defecto de Configuración hacia `destinatarios` y enlaces actualizados.
- [`frontend/src/components/CatalogForms.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/components/CatalogForms.tsx):
  - Modal `PolicyForm` simplificado según `diseno.md` (Flujo 3):
    - Removidos selectores obsoletos de contacto individual y canal.
    - Eliminada dependencia de la función legacy `destinoAviso`.
    - Incorporada la tarjeta `.notice-resolution-card` con ícono `Mail` o `AlertCircle` indicando el destinatario resuelto en tiempo real mediante `resolverDestinatariosAviso(data, product)`.
    - Enlace a `#/configuracion/destinatarios` ("Administrar destinatarios en Configuración →").
    - Invocación de `api.guardarPolitica(product.id, values)` sin campos de contacto.
- [`frontend/src/pages/Purchases.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/Purchases.tsx):
  - Columna "Avisar a" con accesibilidad de teclado: `tabIndex={0}`, `aria-label={`Destinatarios resueltos: ...`}`, `cursor: help` y tooltip con listado completo de emails.

### 1.7 Pruebas
- [`backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java):
  - Integrado `/db/upgrade-destinatarios-avisos.sql` con doble ejecución para verificar idempotencia y aserción de conteo en `destinatario_aviso`.

---

## 2. Decisiones Tomadas

1. **Idempotencia SQL con índices parciales y `NOT EXISTS`**:
   - Para soportar tanto PostgreSQL como H2 (modo PostgreSQL), los índices únicos son parciales (`WHERE categoria_id IS NULL` y `WHERE categoria_id IS NOT NULL`). La inserción inicial de migración utiliza `NOT EXISTS` con comparación `LOWER(TRIM(...))`.
2. **Desacoplamiento limpio con retrocompatibilidad**:
   - `PoliticaReposicion` en dominio mantiene constructores sobrecargados y métodos en desuso devolviendo `null` para evitar incompatibilidades en llamadas binarias o tests anteriores, mientras que su estado persistente queda 100% libre de contacto y canal particular.
3. **Claves únicas de Outbox para envíos múltiples**:
   - Al generarse una alerta para un artículo con N destinatarios en el mismo movimiento o evaluación, se compone la clave como `clave + ":alerta:" + id + ":" + email`. Esto evita colisiones en la restricción `uq_evento_clave` de `evento_integracion`.
4. **Comportamiento ante listas vacías**:
   - Si no hay emails en categoría ni en general, no se generan eventos de outbox pero el episodio de stock bajo se registra con `marcarAlerta(id, true)` en la política, evitando re-alertas en bucle.
5. **Persistencia de categorías en lote**:
   - `guardarPorCategoria(id, emails)` reemplaza la lista de la categoría. Si se envía una lista vacía `[]`, limpia los registros permitiendo que la categoría vuelva a heredar transparentemente de la lista general.
6. **Resolución en tiempo real en UI**:
   - En lugar de mantener campos huérfanos en `PolicyForm`, se calcula `resolverDestinatariosAviso(data, product)` en tiempo de render, informando de inmediato al operador el destino efectivo según la categoría o la lista general de la sucursal.

---

## 3. Desvíos respecto del Plan

- Ninguno. Todos los requerimientos del plan y de las observaciones de revisión de la Vuelta 1 fueron completados estrictamente.

---

## 4. Verificación Realizada en Vuelta 1

1. **Corrección DEF-01 en `PolicyForm` (`CatalogForms.tsx`)**:
   - Se removió la invocación no definida a `destinoAviso`, eliminando el riesgo de `ReferenceError`.
   - Se removieron los selectores obsoletos de contacto y canal.
   - Se integró la tarjeta `.notice-resolution-card` con `Mail` / `AlertCircle`, resumen contextual y enlace a `#/configuracion/destinatarios`.
2. **Corrección de código residual en `GestorInventario.java`**:
   - Eliminada la línea 227 (`d.contactos.resolver(...)`), eliminando acoplamiento muerto a contactos en la configuración de políticas.
3. **Mejoras de UI y accesibilidad**:
   - `App.tsx`: clave `destinatarios` agregada a `subtitles`.
   - `NoticeRecipients.tsx`: estado local sincronizado con `useEffect` ante cambios de `data?.destinatarios`; removidos estilos rígidos inline `marginTop: 28px` y `alignSelf`; incorporada clase `.accordion-trigger-heading`.
   - `Purchases.tsx`: `tabIndex={0}`, `aria-label` y `title` configurados en la celda "Avisar a".
4. **Integridad de archivos**:
   - Verificado que ningún archivo del proyecto contenga bytes nulos ni caracteres de control corruptos.
