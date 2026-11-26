# Revisión — Temporadas de reposición

**Veredicto del reviewer: APROBADO.** Sin hallazgos bloqueantes ni importantes. Capas respetadas (dominio sin Spring/JPA), contrato Strategy intacto, bloqueo de inventario en alta/edición/baja, upgrade idempotente, compatibilidad con clientes que envían `factorEstacional`.

| # | Severidad | Hallazgo | Estado |
| --- | --- | --- | --- |
| 1 | Menor | CA4 pedía 422 y la API responde 409 | Se fijó 409 (conflicto con recurso existente); plan y diseño corregidos |
| 2 | Menor | Objetivos estacionales > 214.748.364 rompían la carga tras migrar | El upgrade los limita; prueba en `UpgradeTemporadasTest` |
| 3 | Menor | Una consulta de temporadas por artículo estacional al evaluar | Se leen una vez por evaluación en `CoordinadorEventosStock` |
| 4 | Menor | `factorVigente` carga todo el inventario para un artículo | Pendiente: igual que el resto de los servicios; requiere consulta puntual en `RepositorioStock` |
| 5 | Menor | Sin prueba de la zona del `Clock` de producción | `AplicacionIntegracionTest.elRelojDeLaAplicacionUsaLaZonaDeLaSucursal` |
| 6 | Menor | `IllegalStateException` desde `@Repository` | `EntidadNoEncontradaException` (404) |
| 7 | Menor | Factor vigente de un combo respondía 200 | Ahora 400, igual que el alta |

Auditoría de diseño (en `diseno.md`): 3 bloqueantes (acciones fuera de vista a 1024 px, foco perdido tras la baja, caja vacía en el modal) y 11 mejoras; corregidos.
