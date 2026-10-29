---
name: equipo
description: Ejecuta un pedido de desarrollo con el equipo completo de agentes (líder, diseñador UX/UI, implementador, tester y reviewer), con artefactos de traspaso en .claude/work/<slug>/ y ciclos de corrección hasta el veredicto del líder. Usar cuando el usuario escribe /equipo <pedido> o pide trabajar "con el equipo" o "con el harness".
argument-hint: <pedido en lenguaje natural>
---

Sos el **orquestador**. Los subagentes no pueden lanzar otros subagentes, así que vos coordinás cada paso con la herramienta Agent y pasás contexto únicamente a través de archivos. No implementes vos mismo lo que corresponde a un agente.

Pedido: $ARGUMENTS

## 0. Preparación

- Elegí un `slug` corto en kebab-case (ej. `alerta-vencimientos`) y creá `.claude/work/<slug>/`.
- Guardá el pedido textual en `.claude/work/<slug>/pedido.md`.
- Leé la memoria compartida del equipo: `node --experimental-sqlite --no-warnings .claude/memoria/memoria.mjs contexto`. Si otro integrante ya trabajó en algo relacionado (`buscar <tema>`), pasale esos ids a cada agente.

## 1. Planificación — `lider`

Invocá `lider` en modo planificación con el pedido y la ruta del directorio. Leé `plan.md`.

- Si tiene `## Preguntas abiertas` que cambian la solución, preguntale al usuario y volvé a invocar al líder con las respuestas.
- Mostrale al usuario los criterios de aceptación en dos o tres líneas y seguí (no esperes aprobación salvo que haya preguntas abiertas).

## 2. Diseño — `disenador-ux-ui` (solo si el plan lo incluye)

Invocalo en modo especificación. Debe producir `diseno.md`.

## 3. Implementación — `implementador`

Invocalo con la ruta del directorio. Debe producir `implementacion.md` y dejar el build en verde.

## 4. Verificación — en paralelo

Lanzá en el mismo mensaje:

- `tester` → `pruebas.md`.
- `reviewer` → guardá su respuesta en `revision.md` y registrá cada hallazgo Bloqueante o Importante como `pendiente` con `--agente reviewer --pedido <slug>` (marcalo `hecha` cuando se corrija).
- `disenador-ux-ui` en modo auditoría, solo si hubo cambios en `frontend/`.

## 5. Ciclo de corrección (máximo 3 vueltas)

Si hay defectos del tester, hallazgos **Bloqueantes** o **Importantes** del reviewer, o hallazgos bloqueantes de la auditoría:

1. Invocá `implementador` pasándole exactamente esos hallazgos (archivo:línea y corrección esperada).
2. Repetí el paso 4 solo con los agentes cuyos hallazgos se corrigieron (el tester siempre vuelve a correr las suites).

Si después de 3 vueltas sigue habiendo bloqueantes, detenete y explicale al usuario qué queda y por qué.

## 6. Cierre — `lider`

Invocá `lider` en modo cierre. Debe escribir `cierre.md` con el veredicto.

## 7. Informe al usuario

- Veredicto y tabla corta de criterios de aceptación.
- Archivos modificados (`git diff --stat`).
- Pendientes o riesgos.
- Confirmá que `.claude/memoria/memoria.db` y `memoria.sql` quedaron actualizados: deben ir en el mismo commit que el cambio, para que las IAs de los otros integrantes tengan el contexto.
- No hagas commit ni push: ofrecelo y esperá la confirmación del usuario.
