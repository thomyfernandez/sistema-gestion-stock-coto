# Rendimiento del frontend y pruebas automatizadas

## Qué se pide y cuándo

Antes, el frontend pedía todo cada 20 segundos —productos, pedidos, el historial completo de movimientos y auditoría, integraciones, configuración— y además **una solicitud de política por cada artículo simple**. Ahora:

| Momento | Solicitudes |
| --- | --- |
| Al iniciar sesión | 7, una por recurso: productos, pedidos, `GET /api/politicas`, categorías, contactos, respaldo de avisos y destinatarios |
| Cada 20 s | Sólo productos y pedidos. Se pausa con la pestaña oculta y, al volver, se pone al día si pasaron 20 s |
| Después de una operación | Sólo el grupo afectado: inventario (movimientos, recepciones, reservas, altas), políticas o configuración. Las promociones sólo renuevan la vista |
| En cada vista | Movimientos, auditoría e integraciones se piden al abrir la vista que los muestra, paginados o limitados |

`store.tsx` agrupa los datos en `inventario`, `politicas` y `configuracion`. `run(accion, mensaje, grupos)` recarga los grupos indicados (por defecto, inventario). Una carga nueva de un grupo reemplaza a la anterior sin descartar los demás. `revision` avanza después de cada operación y, en el sondeo, sólo si cambió el inventario: así las vistas que dependen de ella (actividad, simulación de precios, integraciones) no se vuelven a pedir si no hubo cambios. `useResource(clave, cargar, version)` conserva lo visible mientras recarga.

## API

- `GET /api/politicas`: todas las políticas en una sola respuesta, ordenadas por artículo.
- `GET /api/movimientos?limite=25&pagina=0&stockId=1&tipo=RECEPCION,RESERVA_DIGITAL`
- `GET /api/auditoria?limite=25&pagina=0&stockId=1`

Con `limite` (1 a 500), la respuesta se pagina en la base, del más reciente al más antiguo, y el encabezado `X-Total-Count` informa el total filtrado (también expuesto por CORS). Sin `limite`, se conserva la respuesta anterior: todo el historial en orden de alta. `tipo` acepta varios valores separados por coma.

## Pruebas

| Suite | Comando (desde la raíz) | Qué cubre |
| --- | --- | --- |
| Backend | `mvn -f backend/pom.xml test` | Dominio, servicios, JPA, HTTP, migraciones con H2 y PostgreSQL real |
| Frontend unitarias y de componentes | `cd frontend && npm test` | Vitest + Testing Library sobre jsdom, con un backend simulado que registra cada solicitud |
| Recorridos críticos | `cd frontend && npm run test:e2e` | Playwright contra el backend real |

### PostgreSQL real

`PostgresRealTest` levanta PostgreSQL 17 con Testcontainers, inicializa con `schema.sql` y `data.sql` y arranca el perfil `postgres`: valida el mapeo JPA, login con BCrypt, reserva de combo con outbox, listas de destinatarios, paginación, imágenes `bytea`, doce reservas simultáneas sobre seis unidades (el bloqueo `SELECT … FOR UPDATE` evita sobrevender) y la cadena de migraciones desde v0.1. Requiere Docker en ejecución; sin Docker, la clase se omite y el resto de la suite sigue igual.

Para probar a mano el perfil contra un contenedor:

```sh
docker run --name coto-postgres -e POSTGRES_DB=coto_stock -e POSTGRES_USER=coto -e POSTGRES_PASSWORD=coto -p 5432:5432 -d postgres:17-alpine
docker exec -i coto-postgres psql -v ON_ERROR_STOP=1 -U coto -d coto_stock < backend/src/main/resources/schema.sql
docker exec -i coto-postgres psql -v ON_ERROR_STOP=1 -U coto -d coto_stock < backend/src/main/resources/data.sql
COTO_DB_PASSWORD=coto java -jar backend/target/gestion-stock-0.3.0-SNAPSHOT.jar --spring.profiles.active=postgres
```

### Frontend

`npm test` ejecuta 28 pruebas: listas de emails, prioridad de destinatarios, cliente HTTP (token, errores RFC 9457, sin conexión, sesión vencida, paginación), store (una solicitud por recurso, sondeo de 20 s, pestaña oculta, recarga por grupos, errores, operaciones simultáneas), modal de política, destinatarios y paginación de Actividad. `npm run build` también verifica los tipos de las pruebas.

`npm run test:e2e` levanta el JAR en el puerto 18080 y Vite en el 5174, sin tocar 8080 ni 5173, y recorre: reserva digital, recepción del pedido enviado y lista de destinatarios con el aviso resultante. La primera vez:

```sh
mvn -f backend/pom.xml package -DskipTests
cd frontend
npx playwright install chromium
npm run test:e2e
```

Con `E2E_BACKEND_CMD` se puede reemplazar el comando que inicia el backend.

## Resultados

- `mvn -f backend/pom.xml verify`: **190 pruebas, sin fallos ni omitidas** (8 contra PostgreSQL 17 real).
- `npm test`: **28 pruebas** aprobadas. `npm run build`: correcto.
- `npm run test:e2e`: **3 recorridos** aprobados en Chromium.
- Prueba manual del perfil `postgres` contra un contenedor: inicio, login, reserva persistida, paginación y destinatarios.
