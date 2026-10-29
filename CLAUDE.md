# Sistema de Gestión de Stock — COTO

Backoffice de inventario de sucursal de COTO: stock, recepción de pedidos, compras y reposición, y reservas de Coto Digital. Backend Java 21 + Spring Boot + JPA, frontend React 19 + Vite + TypeScript estricto. Todo el código, los mensajes y la documentación se escriben **en español rioplatense**.

## Comandos

| Objetivo | Comando (desde la raíz) |
| --- | --- |
| Pruebas backend | `mvn -f backend/pom.xml test` |
| Verificación completa + JAR | `mvn -f backend/pom.xml verify` |
| Backend demo (puerto 8080) | `mvn -f backend/pom.xml spring-boot:run` |
| Frontend dev (5173, proxy `/api`) | `cd frontend && npm run dev` |
| Tipos + build de producción | `cd frontend && npm run build` |
| Catálogo del Design System | `cd frontend && npm run dev` → http://127.0.0.1:5173/design-system.html |

Para pruebas manuales que modifican datos, levantar instancias aisladas (backend en `18080`, `API_PROXY_TARGET=http://127.0.0.1:18080`, Vite en `5174`) y no tocar el servidor habitual en `8080`.

## Arquitectura e invariantes

- Capas: `controller` → `service` → `domain` ← `repository`/`adapter`. `config` es la raíz de composición.
- El **dominio no importa Spring, JPA ni SQL**. Servicios y dominio colaboran con la infraestructura mediante puertos (`domain/port`).
- Patrones GoF de la arquitectura: Singleton (`GestorInventario`), Strategy, Decorator, Observer, Composite, Adapter. No romper sus contratos ni sus pruebas.
- `disponible = físico − exhibición − reservado`; no existe columna de disponible. Simples y combos comparten tabla con discriminador; los combos no tienen contadores propios.
- Cambios de esquema: actualizar `schema.sql`, `data.sql` **y** agregar un script `db/upgrade-*.sql` idempotente para bases existentes, con su prueba.
- Errores HTTP en `application/problem+json` (RFC 9457). DTOs validados en `dto/`.
- Frontend: HTTP solo en `api.ts`; estado en `store.tsx`; nunca inventar existencias ni precios en el navegador.

## Design System COTO

Fuente de verdad: [docs/design-system/README.md](docs/design-system/README.md). Implementación: `frontend/src/styles/tokens.css` (tokens) y `frontend/src/styles/components.css` (átomos).

- Usar **siempre** tokens (`var(--…)`); no escribir colores hex, sombras ni tamaños tipográficos sueltos.
- Espaciados en múltiplos de 8 (`--space-*`); 4 px solo para ajustes ópticos internos.
- Proporción 60 % blanco / 30 % neutros / 10 % primario. El rojo se reserva para acciones y estados interactivos.
- Íconos outline `lucide-react` a 24/32/48 px.

## Memoria compartida del equipo (SQLite)

Somos varios integrantes, cada uno con su IA. El contexto común vive en `.claude/memoria/memoria.db` (SQLite) y su espejo `memoria.sql`; **ambos se versionan** y viajan con cada commit. Detalle en [.claude/memoria/README.md](.claude/memoria/README.md).

- **Al empezar cualquier tarea**: `node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs contexto` (convenciones, decisiones vigentes, pendientes abiertos, actividad reciente de los tres).
- **Antes de tocar un tema**: `… memoria.mjs buscar <tema>` para no repetir ni contradecir lo que hizo otro integrante.
- **Al terminar**: registrar `decision`, `aprendizaje`, `pendiente` o `evento` con `… memoria.mjs registrar <tipo> --titulo "…" --detalle "…" --archivos a,b`. Una decisión que cambia otra se registra con `--reemplaza <id>`.
- Incluir `.claude/memoria/` en el mismo commit del cambio. Después de un `git pull`, la CLI incorpora sola las filas nuevas; ante conflicto en el `.db`, usar `sincronizar`.

## Harness: equipo de agentes

Los agentes viven en `.claude/agents/`. El flujo completo se dispara con el skill `/equipo <pedido>` (ver `.claude/skills/equipo/SKILL.md`) y está documentado en [docs/harness-engineering.md](docs/harness-engineering.md).

| Agente | Rol | Escribe |
| --- | --- | --- |
| `lider` | Analiza el pedido, define alcance, criterios de aceptación y plan; da el veredicto final | `plan.md`, `cierre.md` |
| `disenador-ux-ui` | Diseña flujos y pantallas con el Design System COTO; audita la UI implementada | `diseno.md`, estilos |
| `implementador` | Implementa el plan respetando capas, patrones y DS | código de producción |
| `tester` | Escribe y ejecuta pruebas, verifica criterios de aceptación | pruebas, `pruebas.md` |
| `reviewer` | Revisa el diff en busca de defectos y desvíos; no edita | `revision.md` |

Los subagentes no pueden lanzar otros subagentes: la **sesión principal orquesta** y se comunica con cada agente a través de los archivos de `.claude/work/<slug>/`.

## Definición de terminado

1. `mvn -f backend/pom.xml test` en verde si se tocó el backend.
2. `npm run build` en verde si se tocó el frontend.
3. Criterios de aceptación de `plan.md` verificados por el tester, con evidencia.
4. `revision.md` sin hallazgos bloqueantes abiertos.
5. Documentación (`README.md`, `docs/`) actualizada cuando cambia el comportamiento visible o el esquema.
6. No se hace commit ni push sin pedido explícito del usuario.

## Autoría de commits y PRs

- Los commits quedan a nombre de quien los hace (`git config user.name` / `user.email`). **Nunca** agregar `Co-Authored-By` de una IA ni líneas como "Generated with Claude Code", ni en commits ni en descripciones de PR.
- Cada integrante crea su `.claude/settings.local.json` (no se versiona) con:

```json
{ "attribution": { "commit": "", "pr": "", "sessionUrl": false }, "includeCoAuthoredBy": false }
```
