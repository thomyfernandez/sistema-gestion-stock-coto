# Artefactos de traspaso del equipo

Cada pedido ejecutado con `/equipo` crea `.claude/work/<slug>/` con:

| Archivo | Autor | Contenido |
| --- | --- | --- |
| `pedido.md` | orquestador | Pedido textual del usuario |
| `plan.md` | `lider` | Alcance, criterios de aceptación, impacto, tareas y riesgos |
| `diseno.md` | `disenador-ux-ui` | Flujo, pantallas, componentes, estados y auditoría |
| `implementacion.md` | `implementador` | Cambios realizados, decisiones y verificación |
| `pruebas.md` | `tester` | Resultado de suites y evidencia por criterio |
| `revision.md` | `reviewer` (lo guarda el orquestador) | Veredicto y hallazgos con severidad |
| `cierre.md` | `lider` | Veredicto final y pendientes |

Se versionan como evidencia del proceso. Lo que debe sobrevivir al pedido (decisiones, aprendizajes, pendientes) se registra además en la [memoria compartida](../memoria/README.md).
