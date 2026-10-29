# Sistema de Gestión de Stock — COTO C.I.C.S.A.

Backoffice de inventario de sucursal de COTO: existencias, recepción de pedidos, compras y reposición automática, y reservas de Coto Digital. Dominio Java puro con seis patrones GoF, servicios, persistencia JPA, adaptadores de integración, API REST y frontend React con el [Design System COTO](docs/design-system/README.md). Opera una única sucursal, sin entidad `Sucursal`.

## Ejecutar

Requisitos: JDK **21 a 26** y Maven 3.9 o superior. El bytecode se compila para Java 21.

Desde la raíz:

```sh
mvn -f backend/pom.xml verify
java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar
```

El perfil predeterminado `demo` inicia el servidor en **http://localhost:8080**, con H2 temporal, esquema SQL y datos COTO ficticios. No requiere credenciales ni servicios externos. Se detiene con Ctrl+C.

También se puede iniciar directamente para desarrollo:

```sh
mvn -f backend/pom.xml spring-boot:run
```

Consultas iniciales en el navegador:

- [Estado del backend](http://localhost:8080/api/estado).
- [Inventario completo](http://localhost:8080/api/productos).
- [Combo promocional](http://localhost:8080/api/combos/6).
- [Catálogo Coto Digital](http://localhost:8080/api/digital/catalogo).

Ejemplo de reserva por HTTP:

```sh
curl -i http://localhost:8080/api/digital/reservas \
  -H 'Content-Type: application/json' \
  -d '{"stockId":6,"cantidad":2,"motivo":"Compra web de prueba","usuario":"digital.demo"}'
```

Devuelve `201 Created` y los disponibles finales de los artículos y del combo. Los errores usan `application/problem+json`, con código y detalle.

Para probar todos los recorridos, importar [la colección de Postman](backend/api/COTO-Stock.postman_collection.json) o utilizar [las solicitudes HTTP](backend/api/pruebas.http). Para una ejecución determinista de la colección, iniciar un perfil demo nuevo con `--coto.integraciones.automaticas=false`; los envíos posteriores a cada commit siguen activos.

Para ejecutar una demostración completa y terminar el proceso:

```sh
java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar \
  --coto.demo.ejecutar=true \
  --coto.integraciones.automaticas=false \
  --spring.main.web-application-type=none
```

La demo reserva dos combos, persiste los cambios, sincroniza los productos, evalúa reposición y valoriza tres arroces con promociones. Los resultados son: **33 combos disponibles**, **dos pedidos persistidos** y **$2.891,70** para la línea de tres arroces con débito.

## Ejecutar el frontend

Requiere Node.js **22.12 o superior** y npm. Con el backend iniciado en el puerto 8080, abrir otra terminal desde la raíz:

```sh
cd frontend
npm ci
npm run dev
```

Abrir [COTO Gestión de stock](http://127.0.0.1:5173). Configuración incluye categorías y una agenda de contactos internos. En Compras → Reposición se asigna un contacto y un canal de aviso por artículo; sin asignación se usa el de su categoría y, si tampoco tiene, el general. [Uso y migración](docs/contactos-y-avisos.md).

Las cuatro vistas —Stock, Recepción, Compras y Configuración— consumen la API real. Vite reenvía `/api` al backend; no se necesitan servicios externos ni datos simulados en el navegador.

Si el backend usa otro puerto, copiar `frontend/.env.example` a `frontend/.env.local`, cambiar `API_PROXY_TARGET` y reiniciar Vite. Por ejemplo, `API_PROXY_TARGET=http://127.0.0.1:18080`.

Para compilar y revisar el resultado de producción, desde `frontend`:

```sh
npm run build
npm run preview
```

La compilación queda en `frontend/dist`; la vista previa abre en [127.0.0.1:4173](http://127.0.0.1:4173) y utiliza el mismo proxy. El backend debe seguir ejecutándose. Más configuración y recorridos de prueba en [Frontend](docs/etapa-4.md).

## Contenido del backend

| Componente | Implementación |
| --- | --- |
| Singleton | `GestorInventario`: constructor privado, instancia estática privada y `getInstancia()`; Spring registra esa misma instancia |
| Casos de uso | Contrato `GestionInventario`: movimientos, reservas, recepción/cancelación de pedidos, altas de productos y combos, políticas y promociones |
| Strategy | Contextos `ServicioReposicion` y `ServicioNotificaciones`, con estrategias explícitas |
| Decorator | `ServicioValorizacion` construye la cadena desde promociones SQL y redondea el total al final |
| JPA | Diez repositorios concretos, modelos relacionales separados del dominio y unidad de trabajo explícita |
| Adapter | `AdaptadorProveedor`, `AdaptadorProveedorSMS`, `AdaptadorERP`, `AdaptadorCotoDigital` y APIs simuladas de firmas incompatibles |
| Consistencia | Bloqueo SQL del inventario, auditoría y outbox en la misma transacción; entrega después del commit y reintentos |
| REST | 47 operaciones HTTP, DTOs validados, errores RFC 9457, CORS local y simulación de proveedores en demo |

Se conserva `disponible = físico − exhibición − reservado`, sin columna de disponible en `stock`. Simples y combos siguen en una sola tabla con discriminador. Los combos no tienen contadores propios.

## Trabajo en equipo con IA

El repositorio incluye un equipo de agentes para Claude Code (líder, diseñador UX/UI, implementador, tester y reviewer) y una memoria SQLite versionada que comparten las IAs de los tres integrantes. Las reglas del proyecto están en [CLAUDE.md](CLAUDE.md) y el flujo completo se inicia con `/equipo <pedido>`. Antes de empezar a trabajar, consultar el contexto del equipo:

```sh
node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs contexto
```

Detalle en [Equipo de agentes y memoria compartida](docs/harness-engineering.md).

## Organización

```text
frontend/
  src/
    components/         Controles, modales y formularios reutilizables
    pages/              Stock, recepción, compras y configuración
    styles/             Tokens y átomos del Design System COTO, diseño adaptable
    design-system/      Catálogo vivo (design-system.html)
    api.ts              Contratos HTTP tipados y errores
    store.tsx           Lecturas, actualización y coordinación de operaciones
backend/
  api/                  Colección Postman y ejemplos .http
  pom.xml
  src/main/java/ar/com/coto/stock/
    StockApplication.java
    config/             Composición, reintentos y demostración
    controller/         REST para las cuatro vistas, errores y simulación
    dto/                Solicitudes validadas y respuestas planas
    service/            Casos de uso, Singleton y contextos
    domain/             Entidades, patrones, políticas y puertos
    repository/         JPA, modelos relacionales y transacciones
    adapter/            Integraciones externas simuladas
  src/main/resources/
    application.properties
    application-demo.properties
    application-postgres.properties
    schema.sql
    data.sql
    db/upgrade-etapa2.sql
  src/test/java/ar/com/coto/stock/
    domain/             Invariantes y patrones
    application/        Servicios y transacciones completas con Spring/JPA
    persistence/        Scripts SQL y actualización desde el esquema v0.1
    presentation/       Contratos HTTP completos con MockMvc
```

La separación mantiene las cuatro capas solicitadas. `config` es la raíz de composición. El dominio y los servicios colaboran con la infraestructura a través de interfaces, sin importar repositorios concretos. El dominio sigue sin anotaciones de Spring/JPA ni acceso SQL.

## Base de datos

`schema.sql` y `data.sql` inicializan una base vacía. Además de las seis tablas del esquema inicial, se agregan `politica_reposicion`, `promocion`, `evento_integracion` y `bloqueo_operacion`. Los ajustes de backoffice agregan `configuracion_avisos`, `imagen_producto`, `proveedor` (agenda de contactos internos) y `categoria`.

El perfil `demo` usa H2 en memoria y sus datos se recrean con cada ejecución. Para conservar información entre reinicios, está disponible el perfil PostgreSQL. Crear primero una base local y ejecutar **una sola vez**:

```sh
psql -v ON_ERROR_STOP=1 -1 -d coto_stock \
  -f backend/src/main/resources/schema.sql \
  -f backend/src/main/resources/data.sql
```

Configurar `COTO_DB_URL`, `COTO_DB_USER` y `COTO_DB_PASSWORD` en el entorno y arrancar con:

```sh
java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar \
  --spring.profiles.active=postgres
```

El perfil PostgreSQL valida las tablas y no ejecuta inicializaciones al arrancar. Si la base ya contiene el esquema inicial (**v0.1**) y sus datos de muestra, utilizar en su lugar:

```sh
psql -v ON_ERROR_STOP=1 -1 -d coto_stock \
  -f backend/src/main/resources/db/upgrade-etapa2.sql \
  -f backend/src/main/resources/db/upgrade-backoffice.sql \
  -f backend/src/main/resources/db/upgrade-proveedores.sql \
  -f backend/src/main/resources/db/upgrade-categorias.sql
```

Estas actualizaciones agregan las tablas/configuraciones de reposición, avisos, imágenes, contactos y categorías, y encolan pedidos pendientes sin borrar el inventario existente. Se ejecuta una sola vez y está pensada para bases creadas con el esquema v0.1. El modo `-1` aplica todo en una transacción. Si ya se aplicaron migraciones anteriores, ejecutar solamente las que falten, en el orden indicado.

## Verificación

```sh
mvn -f backend/pom.xml test
```

La suite incluye las pruebas de dominio y pruebas de integración reales con Spring, Hibernate y H2. Verifica reservas concurrentes, rollback tardío, auditoría correlacionada, reposición sin duplicados, reintentos FIFO, episodios de alerta persistidos, recepción única, alta de combos y promociones acumulables. Los proveedores son simulados: no se envían correos, SMS, WhatsApp ni pedidos reales.

Verificación actual: **153 pruebas aprobadas, sin fallos ni errores** (65 HTTP), JAR empaquetado. Incluye regresiones por renovación de conexiones de H2 y pruebas de avisos compartidos, contactos internos, destinatarios por artículo/categoría, clasificación del catálogo e imágenes de productos. La validación de la API también comprobó **37 solicitudes de Postman contra el servidor HTTP real**. La prueba del servidor usó el puerto 18080; el puerto predeterminado para uso normal es 8080.

Los scripts y el mapeo JPA se verifican con H2 compatible con PostgreSQL. El perfil PostgreSQL está configurado; todavía no se validó contra un servidor PostgreSQL real.

## Documentación

- [Design System COTO: color, tipografía, grillas, elevaciones, íconos y átomos](docs/design-system/README.md).

- [Equipo de agentes (harness) y memoria compartida](docs/harness-engineering.md).

- [Categorías: clasificación, filtros y destinatarios compartidos](docs/categorias.md).

- [Contactos internos y avisos por producto](docs/contactos-y-avisos.md).

- [Ajustes de backoffice: Stock unificado, avisos compartidos, imágenes y actualización de la base](docs/ajustes-backoffice.md).

- [Corrección de H2: recepción de pedidos después de renovar conexiones](docs/correccion-h2.md).

- [v0.4 — Frontend React: vistas, estado, ejecución y validación](docs/etapa-4.md).
- [v0.3 — API REST: endpoints, cuerpos JSON y pruebas de los seis patrones](docs/etapa-3.md).
- [v0.2 — Aplicación: contratos, patrones, transacciones y decisiones](docs/etapa-2.md).
- [v0.1 — Dominio y modelo SQL inicial](docs/etapa-1.md).

El frontend se verificó con TypeScript, compilación de producción y recorridos reales en navegador de escritorio y móvil. Los detalles y las capturas están en la documentación del frontend.
