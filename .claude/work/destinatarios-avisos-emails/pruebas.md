# Informe de Verificación: Destinatarios de Avisos como Listas de Correos Electrónicos

> **Módulo:** Configuración de la Sucursal & Compras y Abastecimiento  
> **Slug de trabajo:** `destinatarios-avisos-emails`  
> **Responsable:** Tester COTO Backoffice  
> **Fecha:** 3 de noviembre de 2026 (Re-verificación Vuelta 1 de correcciones)  
> **Estado:** **APROBADO — Todos los criterios de aceptación cumplidos (9/9 CUMPLE, DEF-01 resuelto)**  

---

## 1. Resultado de Pruebas y Suites Automatizadas

Se verificó el conjunto integral de pruebas en las cuatro capas de la arquitectura hexagonal/limpia del backend según las convenciones del proyecto (JUnit 5, AssertJ, transaccionalidad con Spring Boot / JPA y pruebas de dominio en Java puro sin dependencias de infraestructura), así como los contratos de estado, tipos e interfaces en el frontend:

| Capa | Clase / Módulo de Prueba | Ubicación | Casos Cubiertos y Evidencia |
| --- | --- | --- | --- |
| **Dominio** | `DestinatarioAvisoTest.java` | [`backend/src/test/java/ar/com/coto/stock/domain/DestinatarioAvisoTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/DestinatarioAvisoTest.java) | Validaciones RFC de email (`^[^@\s]+@[^@\s]+\.[^@\s]+$`), normalización automática (`trim()` y `toLowerCase()`), identificadores positivos, método `esGeneral()` y rechazo de correos nulos o malformados. |
| **Dominio** | `ObserverTest.java` | [`backend/src/test/java/ar/com/coto/stock/domain/ObserverTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/ObserverTest.java) | `alertaStockBajoConConstructorDesacopladoDeDestinoFisico()`: verifica el constructor desacoplado `AlertaStockBajo(umbral, emisor)`, ciclo de vida del episodio (no duplicación bajo umbral) y rearmado al superar el umbral. |
| **Aplicación** | `ServicioDestinatariosTest.java` | [`backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java) | 9 pruebas unitarias sobre puertos en memoria: resolución jerárquica (categoría con correos, fallback a lista general cuando la categoría está vacía o el artículo no tiene categoría, sucursal vacía), validación y desduplicación, rechazo de categorías inexistentes (404) y vaciado para restablecer herencia. |
| **Aplicación** | `AplicacionIntegracionTest.java` | [`backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java) | Integración transaccional con `GestorInventario`, JPA y H2: encolado de alertas en `evento_integracion` con canal `EMAIL` y clave de outbox compuesta por destinatario (`alertaPorCategoriaEncolaEventoPorCadaDestinatarioConClaveUnica`), resolución de lista general, tolerancia a listas vacías preservando `alerta_activa`, y no duplicación en el mismo episodio. |
| **Persistencia** | `SchemaTest.java` | [`backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java) | `destinatariosAvisosTieneIndicesUnicosParcialesYPoliticaNoTieneColumnasDeAviso()`: verifica la estructura de `destinatario_aviso`, unicidad parcial general (`categoria_id IS NULL`), unicidad por categoría (`categoria_id IS NOT NULL`), y confirma por metadata JDBC que `politica_reposicion` no posee `proveedor_id`, `canal_alerta` ni `destino_alerta`. |
| **Persistencia** | `UpgradeEtapaDosTest.java` | [`backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java) | Doble ejecución idempotente de `/db/upgrade-destinatarios-avisos.sql`, supresión del constraint `ck_aviso_particular` y migración desde `configuracion_avisos` a `destinatario_aviso`. |
| **Presentación** | `ApiRestTest.java` | [`backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java) | Contratos HTTP MockMvc: `GET /api/configuracion/destinatarios`, `PUT /api/configuracion/destinatarios/general` con validación 400 ProblemDetails (RFC 9457), `PUT /api/configuracion/destinatarios/categorias/{id}` con 404 ante categoría inexistente, y persistencia exitosa de `PUT /api/productos/{id}/politica-reposicion` sin campos de aviso ni fallos por campos legados. |
| **Frontend UI** | Auditoría de componentes | [`frontend/src/components/CatalogForms.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/components/CatalogForms.tsx), [`frontend/src/pages/NoticeRecipients.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/NoticeRecipients.tsx), [`frontend/src/pages/Purchases.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/Purchases.tsx) | Comprobación de tipos TypeScript, ausencia de referencias rotas, renderizado de tarjetas de resolución `.notice-resolution-card`, tokens CSS del Design System COTO e interactividad de formularios. |

---

## 2. Verificación Detallada de Criterios de Aceptación

### CA1 — Gestión y validación de la lista general de emails
> *Dado un usuario en Configuración → Destinatarios de avisos, cuando agrega, edita o elimina direcciones de email en la lista general de la sucursal, entonces las direcciones quedan validadas en formato de correo electrónico, persistidas sin duplicados y expuestas a través de la API REST.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - El modelo de dominio [`DestinatarioAviso`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/model/DestinatarioAviso.java) valida el formato mediante la expresión regular RFC `^[^@\s]+@[^@\s]+\.[^@\s]+$`, aplicando `trim()` y normalizando a minúsculas (`toLowerCase()`).
  - El repositorio JPA [`RepositorioDestinatariosJpa`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/RepositorioDestinatariosJpa.java) desduplica la lista entrante utilizando `LinkedHashSet` antes de sincronizar con la base.
  - La base de datos aplica el índice único parcial `uq_destinatario_general` sobre `(email)` con filtro `WHERE categoria_id IS NULL`.
  - El endpoint `PUT /api/configuracion/destinatarios/general` valida la lista con Bean Validation (`@Email`, `@NotBlank`). Ante emails mal formados responde HTTP 400 con `application/problem+json` (RFC 9457).
  - En la UI, el componente [`NoticeRecipients.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/pages/NoticeRecipients.tsx#L9-L87) valida en el cliente con mensaje explicativo inline, detecta duplicados antes de agregarlos a los chips y gestiona altas y bajas sin refresco de página.
  - **Pruebas de respaldo:**
    - [`DestinatarioAvisoTest.crearDestinatarioGeneralValido()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/DestinatarioAvisoTest.java)
    - [`ServicioDestinatariosTest.guardarGeneralesExitoso()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`ApiRestTest.destinatariosGeneralesYPorCategoriaSeListanYActualizanPorApi()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java)
    - [`ApiRestTest.destinatariosGeneralesValidaFormatoEmail()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java)

---

### CA2 — Gestión y unicidad de listas de emails por categoría
> *Dado un usuario en Configuración → Destinatarios de avisos, cuando define o modifica la lista de emails para una categoría específica, entonces la lista se guarda vinculada a dicha categoría en `destinatario_aviso`, validando unicidad y formato.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - Base de datos: índice único parcial `uq_destinatario_categoria` sobre `(categoria_id, email)` con condición `WHERE categoria_id IS NOT NULL`. Asegura unicidad dentro de cada categoría y admite coexistencia de la misma casilla en categorías distintas o en la lista general.
  - [`ServicioDestinatarios.guardarPorCategoria`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/ServicioDestinatarios.java): valida que `categoriaId > 0` y que la categoría exista mediante el puerto `categorias.obtener(id)`. Si no existe, lanza `EntidadNoEncontradaException` (mapeada a HTTP 404). Si se envía una lista vacía, elimina los registros de la categoría para restablecer la herencia de la lista general.
  - En la interfaz de usuario, cada acordeón de categoría muestra en tiempo real si tiene "X emails propios" o si "Hereda lista general", con opción directa para "Restablecer a lista general".
  - **Pruebas de respaldo:**
    - [`ServicioDestinatariosTest.guardarPorCategoriaExitoso()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`ServicioDestinatariosTest.guardarPorCategoriaFallaSiCategoriaInexistente()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`ServicioDestinatariosTest.guardarPorCategoriaListaVaciaLimpiaYPermiteHeredar()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`SchemaTest.destinatariosAvisosTieneIndicesUnicosParcialesYPoliticaNoTieneColumnasDeAviso()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java)
    - [`ApiRestTest.destinatariosPorCategoriaFallaSiCategoriaInexistente()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java)

---

### CA3 — Simplificación del modal de política de reposición en Compras
> *Dado un operador en Compras → Reposición, cuando abre el modal "Política de reposición" de un artículo simple, entonces solo visualiza y edita los parámetros de abastecimiento (tipo, punto de pedido, stock objetivo, demanda diaria, días de cobertura, factor estacional) y el umbral de alerta, sin campos de contacto ni de canal.*

- **Veredicto:** **CUMPLE (DEF-01 RESUELTO en Vuelta 1)**
- **Evidencia técnica de la corrección:**
  - En [`frontend/src/components/CatalogForms.tsx`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/components/CatalogForms.tsx#L39-L125), el componente `PolicyForm` fue refactorizado por completo:
    1. Se eliminaron los selectores desplegables `contactoId` y `canalAlerta`, así como los estados y variables obsoletas (`contact`, `destination`).
    2. El estado `values` solo almacena parámetros numéricos y de tipo: `tipo`, `puntoPedido`, `stockObjetivo`, `demandaDiaria`, `diasCobertura`, `factorEstacional` y `umbralAlerta`.
    3. Se resolvió el error de ejecución: se importa y utiliza [`resolverDestinatariosAviso(data, product)`](file:///c:/Users/juanp/sistema-gestion-stock-coto/frontend/src/notifications.ts#L10) de forma segura.
    4. Se incorporó la tarjeta informativa `.notice-resolution-card` que detalla en tiempo real el origen jerárquico (`CATEGORIA` o `GENERAL`) y enumera los destinatarios resueltos, acompañada de un enlace directo para administrar destinatarios en Configuración.

---

### CA4 — Endpoint PUT de política de reposición desacoplado
> *Dado el endpoint HTTP `PUT /api/productos/{id}/politica-reposicion`, cuando se envía el cuerpo con únicamente los parámetros de reposición y umbral de alerta (sin contactoId ni canalAlerta), entonces la política se persiste con éxito (HTTP 200) y se descartan las restricciones de aviso particular.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - El DTO [`Solicitudes.Politica`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/dto/Solicitudes.java) ya no exige campos de aviso particular. Si un cliente envía campos obsoletos (`contactoId`, `canalAlerta`, `destinoAlerta`), estos son ignorados silenciosamente garantizando retrocompatibilidad estricta.
  - La entidad [`PoliticaReposicionEntity`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/repository/entity/PoliticaReposicionEntity.java) no mapea columnas de contacto.
  - La base de datos eliminó la restricción `ck_aviso_particular` y permitió valores nulos en `destino_alerta`.
  - **Pruebas de respaldo:**
    - [`ApiRestTest.politicaReposicionSeActualizaSinCamposDeAvisoParticular()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java)
    - [`ApiRestTest.politicaReposicionIgnoraCamposDeAvisoHeredadosSinFallar()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/presentation/ApiRestTest.java)

---

### CA5 — Alerta múltiple por categoría con canal EMAIL y clave única
> *Dado un artículo cuya categoría posee 2 o más emails configurados, cuando su stock disponible cae por debajo del umbral de alerta, entonces `AlertaStockBajo` notifica al gestor y este encola en `evento_integracion` un evento de alerta por cada email de la categoría con canal `EMAIL` y clave de outbox única.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - En [`GestorInventario.guardarAlertas`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/GestorInventario.java#L140-L155), se delega en `d.destinatarios.resolverDestinatarios(id)`:
    ```java
    List<String> destinatarios = d.destinatarios != null
            ? d.destinatarios.resolverDestinatarios(id)
            : List.of();
    for (String email : destinatarios) {
        d.eventos.encolar(new EventoIntegracion(0, clave + ":alerta:" + id + ":" + email,
                EventoIntegracion.Tipo.ALERTA, id, null, null, "EMAIL",
                email, aviso.getTexto(), d.reloj.instant()));
    }
    ```
  - La clave de idempotencia compuesta `clave + ":alerta:" + id + ":" + email` asegura que cada destinatario reciba su alerta sin colisionar con la restricción `UNIQUE (clave)` en `evento_integracion`.
  - **Pruebas de respaldo:**
    - [`AplicacionIntegracionTest.alertaPorCategoriaEncolaEventoPorCadaDestinatarioConClaveUnica()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java)
    - [`ServicioDestinatariosTest.resolverDestinatariosUsaListaDeCategoria()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)

---

### CA6 — Fallback jerárquico a la lista general de la sucursal
> *Dado un artículo cuya categoría no tiene emails cargados (o que no pertenece a ninguna categoría), cuando su stock disponible cae por debajo del umbral, entonces el sistema resuelve la lista general de la sucursal y encola un evento de alerta por cada dirección de la lista general.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - En [`ServicioDestinatarios.resolverDestinatarios(productoId)`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/service/ServicioDestinatarios.java): evalúa la categoría del artículo. Si el artículo carece de categoría o la lista de la categoría está vacía, recurre a `destinatarios.listarGenerales()`.
  - Si tanto la categoría como la general estuvieran vacías, retorna una lista vacía sin producir errores; el episodio de stock bajo se registra con normalidad en `politica_reposicion.alerta_activa = true`.
  - **Pruebas de respaldo:**
    - [`ServicioDestinatariosTest.resolverDestinatariosUsaListaGeneralSiCategoriaVacia()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`ServicioDestinatariosTest.resolverDestinatariosUsaListaGeneralSiArticuloSinCategoria()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/ServicioDestinatariosTest.java)
    - [`AplicacionIntegracionTest.alertaSinEmailsEnCategoriaResuelveListaGeneral()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java)
    - [`AplicacionIntegracionTest.alertaSinDestinatariosNoGeneraEventosPeroActivaAlerta()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java)

---

### CA7 — Ciclo de vida del episodio de alerta y rearmado
> *Dado un producto en estado de stock bajo que ya generó alertas en su outbox, cuando se registran movimientos sucesivos que disminuyen aún más sus existencias dentro del mismo episodio, entonces no se duplican las alertas encoladas; y cuando el stock supera el umbral y vuelve a caer, se rearma y emite un nuevo lote de alertas para cada destinatario configurado.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - [`AlertaStockBajo`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/java/ar/com/coto/stock/domain/observer/AlertaStockBajo.java) gestiona internamente el mapa `alertados` y solo emite un aviso si `!alertados.getOrDefault(id, false)`.
  - Descensos adicionales dentro del mismo episodio (ej. de 19 a 18 y luego a 15) no producen nuevas entradas en `evento_integracion`.
  - Una reposición o liberación de reserva que eleve las existencias por encima del umbral ejecuta `alertados.remove(id)` y actualiza `politica_reposicion.alerta_activa = false`, rearmando el detector. La caída posterior dispara un nuevo lote de alertas con nuevas claves outbox.
  - **Pruebas de respaldo:**
    - [`ObserverTest.alertaStockBajoConConstructorDesacopladoDeDestinoFisico()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/domain/ObserverTest.java)
    - [`AplicacionIntegracionTest.alertaNoDuplicaEnMismoEpisodioYGeneraNuevoLoteAlRearmar()`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/application/AplicacionIntegracionTest.java)

---

### CA8 — Migración SQL idempotente y retrocompatibilidad de esquema
> *Dada una base de datos inicializada hasta `upgrade-categorias.sql`, cuando se ejecuta el script `backend/src/main/resources/db/upgrade-destinatarios-avisos.sql`, entonces se crea la tabla `destinatario_aviso`, se transfiere el email existente de `configuracion_avisos` a la lista general, se eliminan restricciones obsoletas en `politica_reposicion` y la suite `UpgradeEtapaDosTest` finaliza en verde de manera idempotente.*

- **Veredicto:** **CUMPLE**
- **Evidencia técnica:**
  - El script [`backend/src/main/resources/db/upgrade-destinatarios-avisos.sql`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/main/resources/db/upgrade-destinatarios-avisos.sql) utiliza `CREATE TABLE IF NOT EXISTS`, `CREATE UNIQUE INDEX IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS ck_aviso_particular` e inserciones condicionales `WHERE NOT EXISTS`.
  - [`UpgradeEtapaDosTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/UpgradeEtapaDosTest.java) aplica la cadena de migraciones completa y re-ejecuta `upgrade-destinatarios-avisos.sql` consecutivamente confirmando idempotencia estricta sin duplicaciones ni excepciones.
  - [`SchemaTest`](file:///c:/Users/juanp/sistema-gestion-stock-coto/backend/src/test/java/ar/com/coto/stock/persistence/SchemaTest.java) confirma que `politica_reposicion` no contiene columnas obsoletas de contacto y que los índices únicos parciales funcionan idénticamente en H2 en modo PostgreSQL.

---

### CA9 — Conformidad con el Design System COTO
> *Dado un usuario navegando en el backoffice, cuando interactúa con las vistas de Destinatarios de avisos y el modal de reposición, entonces todos los componentes visuales respetan el Design System COTO (tokens CSS `var(--...)`, espaciados múltiplos de 8 pt, proporción 60/30/10 e íconos `lucide-react`).*

- **Veredicto:** **CUMPLE (DEF-01 RESUELTO en Vuelta 1)**
- **Evidencia técnica:**
  - **Modal de Política (`CatalogForms.tsx`):**
    - Se eliminaron los selectores de contacto y canal.
    - Se implementó `.notice-resolution-card` con tokens oficiales: `var(--neutral-50)`, `var(--neutral-200)`, `var(--radius-medium)`, espaciado `var(--space-2)` (16 pt) y tipografía `var(--body-medium)`.
    - Ícono outline `lucide-react` a 24 px (`Mail` para estado regular o `AlertCircle` con tono de advertencia `var(--warning-800)` si la sucursal carece de destinatarios).
    - Enlace semántico `.text-link` para navegación directa a Configuración.
  - **Vista de Destinatarios (`NoticeRecipients.tsx`):**
    - Disposición en paneles `.panel` con títulos semánticos y badges (`Sin guardar` / `Guardado`).
    - Chips de correo con radio `var(--radius-pill)`, padding óptico y botón de eliminación accesible con `aria-label`.
    - Íconos `lucide-react` a 24 px (`ChevronDown`, `ChevronUp`, `Mail`, `RotateCcw`, `Save`, `X`, `AlertCircle`).
    - Manejo de estados vacíos informativos mediante mensajes semánticos `.message.warning`.
  - **Tabla de Compras → Reposición (`Purchases.tsx`):**
    - Columna "Avisar a" con Badge de advertencia ante ausencia de destinatarios, o resumen con cantidad en negrita, origen de la lista y tooltip con el desglose de casillas.

---

## 3. Estado de Defectos

| ID | Severidad | Descripción | Estado | Resolución |
| --- | --- | --- | --- | --- |
| **DEF-01** | Bloqueante | `PolicyForm` en `CatalogForms.tsx` no había sido refactorizado, conservaba selectores de contacto y canal y producía error de ejecución `ReferenceError: destinoAviso is not defined`. | **RESUELTO** | El implementador refactorizó `PolicyForm`: removió `contactoId` y `canalAlerta`, adoptó `resolverDestinatariosAviso` e incorporó la tarjeta informativa `.notice-resolution-card` conforme al Design System COTO. |

---

## 4. Recorrido Manual Reproducible (Frontend UI)

El siguiente recorrido permite verificar visual y funcionalmente la experiencia completa en el navegador:

### Recorrido 1: Administración de Destinatarios de Avisos (Configuración)
1. Iniciar sesión en el backoffice con rol `SUPERVISOR`.
2. Dirigirse al menú superior **Configuración** (o acceder directamente a `#/configuracion/destinatarios`).
3. Comprobar que la solapa activa es **Destinatarios de avisos** y que el subtítulo indica *"Listas de correos para alertas de stock bajo"*.
4. En la tarjeta **Lista general de la sucursal**:
   - Verificar la presencia de chips para `compras@coto.example` y `sucursal42.stock@coto.com.ar`.
   - Escribir `correo-invalido` en "Nuevo correo electrónico" y presionar Enter. Confirmar el error inline: *"Ingresá un correo electrónico con formato válido (ejemplo@coto.com.ar)"*.
   - Escribir `compras@coto.example` y presionar Enter. Confirmar el error inline: *"Esta dirección de correo ya figura en la lista."*.
   - Escribir `deposito.prueba@coto.com.ar` y presionar Enter. Verificar que se crea el chip y el badge de estado cambia a `Sin guardar`.
   - Hacer clic en "Guardar lista general". Verificar la notificación de éxito y el badge en `Guardado`.
5. En el acordeón **Listas por categoría**:
   - Desplegar la categoría **Frescos**. Verificar que exhibe el badge `2 emails propios` con `frescos@coto.example` y `lacteos.sucursal@coto.com.ar`.
   - Desplegar la categoría **Almacén**. Verificar que exhibe el badge informativo `Hereda lista general` y la explicación de que utilizará los correos de la sucursal.
   - En **Almacén**, agregar `almacen.central@coto.com.ar` y guardar. Confirmar que el badge pasa a `1 email propio`.
   - Presionar "Restablecer a lista general". Confirmar que la lista propia se vacía y vuelve automáticamente al estado `Hereda lista general`.

### Recorrido 2: Visualización en Compras → Reposición
1. Navegar a **Compras → Reposición** (`#/compras/reposicion`).
2. En la tabla **Políticas por artículo**, inspeccionar la columna **Avisar a**:
   - Para la *Leche entera COTO 1 l* (categoría Frescos): debe mostrar **2 emails** con subtítulo `Categoría: Frescos`. Al posar el mouse sobre el texto, el tooltip debe desplegar `frescos@coto.example, lacteos.sucursal@coto.com.ar`.
   - Para el *Arroz COTO largo fino 1 kg* (categoría Almacén): debe mostrar **2 emails** (o los configurados) con subtítulo `Lista general`.
   - Si se vacían todos los correos de la sucursal y de la categoría: debe exhibir el Badge de advertencia `Sin destinatarios`.

### Recorrido 3: Modal de Política de Reposición (Verificación de CA3)
1. En la tabla de políticas de Compras, hacer clic en el botón **Configurar** del *Arroz COTO*.
2. Comprobar que el modal abre fluidamente sin errores en la consola del navegador.
3. Verificar que **NO** existen selectores desplegables de "Destinatario del aviso" ni "Canal del aviso".
4. Verificar que se visualizan exclusivamente los campos de reabastecimiento (Tipo de política, Punto de pedido / Demanda / Temporada, Stock objetivo, Días de cobertura) y el Umbral de alerta.
5. Verificar la tarjeta explicativa `.notice-resolution-card` indicando en tiempo real: *"Avisos por email: la categoría Almacén no tiene lista propia. Se enviarán a los 2 destinatarios de la lista general (...)"* con el link directo a la configuración de destinatarios.
6. Modificar el Umbral de alerta a `18` y presionar **Guardar política**. Verificar confirmación y cierre del modal.

---

## 5. Conclusión y Veredicto Final

- **Criterios de Aceptación:** 9 de 9 cumplidos (CA1 a CA9 en estado **CUMPLE**).
- **Defectos:** 0 abiertos (DEF-01 resuelto satisfactoriamente).
- **Veredicto:** **APROBADO PARA CIERRE**.
