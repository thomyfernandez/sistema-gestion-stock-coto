# Memoria compartida del equipo

Somos tres integrantes y cada uno trabaja con su propia IA. Para que todas tengan el mismo contexto, lo que se decide, se aprende y queda pendiente se guarda en una base **SQLite versionada en el repositorio**.

| Archivo | Qué es |
| --- | --- |
| `memoria.db` | Base SQLite que consultan los agentes. Se versiona. |
| `memoria.sql` | Espejo en texto, una fila por línea. Git lo combina con `merge=union` (ver `.gitattributes`), así que las entradas de los tres se suman sin conflictos. |
| `memoria.mjs` | CLI sin dependencias (`node:sqlite`, Node 22.12+). |

Cada escritura actualiza los dos archivos. Al abrirse, la CLI incorpora cualquier fila de `memoria.sql` que falte en la base, así que después de un `git pull` alcanza con usarla.

## Uso

```sh
node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs contexto
```

| Comando | Para qué |
| --- | --- |
| `contexto [--pedido slug]` | Resumen al empezar: convenciones, decisiones vigentes, tareas abiertas, aprendizajes y actividad reciente |
| `registrar <tipo> --titulo "…" [--detalle "…"] [--agente x] [--pedido slug] [--archivos a,b] [--reemplaza id]` | Agrega una entrada. El autor se toma de `git config user.name` |
| `estado <id> abierta\|en-curso\|hecha\|descartada` | Actualiza una tarea o pendiente |
| `buscar <texto>` · `listar [--tipo] [--pedido] [--autor]` · `ver <id>` | Consultas |
| `sincronizar` | Descarta `memoria.db` y la reconstruye desde `memoria.sql` |

Tipos:

- `convencion`: regla del equipo que todos respetan (estilo, nombres, flujo de ramas).
- `decision`: elección técnica o de diseño con su motivo. Para cambiarla, registrar una nueva con `--reemplaza <id>`; la anterior deja de mostrarse en el contexto.
- `aprendizaje`: algo no obvio que costó descubrir (un bug de H2, una trampa del build).
- `tarea` / `pendiente`: trabajo por hacer, con estado.
- `evento`: qué se hizo (cierre de un pedido de `/equipo`, una migración aplicada).

Las entradas son cortas y concretas: qué, por qué y dónde (`--archivos`). No duplicar lo que ya dice el código, `CLAUDE.md` o el historial de git.

## Después de un merge

`memoria.sql` se combina solo. Si git marca conflicto en `memoria.db` (binario), quedate con cualquiera de las dos versiones y reconstruí:

```sh
git checkout --theirs .claude/memoria/memoria.db
node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs sincronizar
git add .claude/memoria/
```
