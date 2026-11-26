# Pruebas — Temporadas de reposición

| Suite | Resultado |
| --- | --- |
| `mvn -f backend/pom.xml verify` | 225 pruebas, 0 fallas; 8 de `PostgresRealTest` omitidas (sin Docker en la máquina de verificación) |
| `npm run build` | OK |
| `npm test` (Vitest) | 37 de 37 |
| `npm run test:e2e` (Playwright, backend 18080 / Vite 5174) | 4 de 4 |

Robustez frente a la fecha: se corrió la suite con la temporada del pan dulce fuera del día de hoy; `AplicacionIntegracionTest` dependía de la fecha y ahora fija la temporada a todo el año.

## Criterios de aceptación

| CA | Evidencia |
| --- | --- |
| CA1 dentro/fuera de temporada | `TemporadasTest.estacionalUsaElFactorDeLaTemporadaSoloDentroDeElla`, `TemporadasApiTest.laEvaluacionUsaElFactorSoloDentroDeLaTemporada` |
| CA2 cruce de año 15/11 → 06/01 | `TemporadasTest.temporadaDeDiciembreAEneroCruzaElAnio` |
| CA3 artículo sobre categoría | `TemporadasTest.laTemporadaDelArticuloPrevaleceSobreLaDeSuCategoria`, `TemporadasApiTest.factorVigenteInformaOrigenArticuloCategoriaOSinTemporada` |
| CA4 409 superposición, 400 validaciones | `TemporadasApiTest.superposicionDelMismoAlcanceDevuelveConflicto`, `validacionesDevuelvenProblemJson` |
| CA5 upgrade idempotente | `UpgradeTemporadasTest`, `PostgresRealTest.migracionesDesdeV01CorrenEnPostgres` (requiere Docker) |
| CA6 roles | `AutenticacionYRolesTest.temporadasValidanRol`; UI en `Seasons.test.tsx` y e2e |
| CA7 suites en verde | tabla de arriba |
| CA8 varios artículos | `TemporadasApiTest.unaTemporadaParaVariosArticulos`, `Seasons.test.tsx` |

Verificación manual en navegador (instancia aislada 18090/5180): alta con varios artículos, choque 409 nombrando el artículo en común, tabla a 1024 px.
