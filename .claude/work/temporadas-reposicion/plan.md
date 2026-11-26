# Plan — Temporadas de reposición estacional

## Alcance

1. **Dominio** (`domain/reposicion`, sin Spring/JPA):
   - `Temporada` (record): `id`, `nombre` (≤ 80), alcance **artículo** (`stockId`) **o** **categoría** (`categoriaId`), exactamente uno; `desde` y `hasta` como `MonthDay` (se repiten cada año); `factor` (`BigDecimal`, > 0 y ≤ 10, 4 decimales). `incluye(LocalDate)`: si `desde ≤ hasta`, el día está en `[desde, hasta]`; si `desde > hasta` la temporada cruza el año (ej. 15/11 → 06/01). Un 29/02 como límite es válido; en años no bisiestos `hasta = 29/02` termina el 28/02.
   - `CalendarioTemporadas`: temporadas que aplican a un artículo. Resolución jerárquica, como los avisos: **temporada del artículo** vigente → **temporada de su categoría** vigente → **factor 1**. Devuelve `FactorVigente(factor, temporada|null, origen ARTICULO|CATEGORIA|SIN_TEMPORADA)`.
   - Dentro del mismo alcance (mismo artículo o misma categoría) las temporadas **no se superponen**: así la resolución es determinista. Se valida al guardar (error 409 `TEMPORADA_SUPERPUESTA` con mensaje accionable; decisión de cierre: es un conflicto con un recurso existente).
   - `ReposicionEstacional` recibe `objetivoBase`, `CalendarioTemporadas` y `Clock`; toma la fecha con `LocalDate.now(reloj)` en cada cálculo (nunca `LocalDate.now()` sin reloj). Se conserva el constructor `(objetivoBase, factor)` como factor fijo todo el año, por contrato y pruebas existentes. Valida desborde con el factor máximo del calendario al construir.
   - `PoliticaReposicion` pierde `factorEstacional`; `crearEstrategia(CalendarioTemporadas, Clock)`.
2. **Puerto + JPA**: `RepositorioTemporadas` (`listar`, `listarPara(stockId, categoriaId)`, `obtener`, `guardar`, `eliminar`), `TemporadaEntity`, `RepositorioTemporadasJpa`.
3. **Servicio**: `ServicioTemporadas` (`GestionTemporadas`) con CRUD, validación de alcance existente (artículo simple o categoría), superposición y desborde del objetivo de las políticas estacionales afectadas; `factorVigente(stockId)`. `ServicioReposicion.estrategiaPara(politica, stock)` arma el calendario y usa su `Clock`. El reloj de la aplicación pasa a la zona de la sucursal (`coto.sucursal.zona-horaria`, por defecto `America/Argentina/Buenos_Aires`) para que el cambio de día sea el local y no el de UTC.
4. **API** (`TemporadaController`, `@Tag "Temporadas de reposición"`):
   - `GET /api/temporadas` → `Temporada[]` con `vigente` calculado por el servidor.
   - `POST /api/temporadas` (201 + Location), `PUT /api/temporadas/{id}`, `DELETE /api/temporadas/{id}` (204). Escritura: COMPRAS y SUPERVISOR.
   - `GET /api/productos/{id}/factor-estacional` → `{ stockId, fecha, factor, temporadaId, nombre, origen }`.
   - Cuerpo: `{ nombre, stockId?, categoriaId?, desdeDia, desdeMes, hastaDia, hastaMes, factor }`; fechas inválidas (31/04) → 400 problem+json.
   - `Politica` (solicitud y respuesta) deja de tener `factorEstacional`; un cliente viejo que lo envíe no falla (se ignora).
5. **Esquema**: tabla `temporada_reposicion` en `schema.sql`; `data.sql` con Pan dulce (id 5) en "Navidad y Reyes" 15/11 → 06/01 ×1,5; `db/upgrade-temporadas.sql` idempotente: crea la tabla, convierte cada política `ESTACIONAL` con factor ≠ 1 en una temporada de todo el año (01/01 → 31/12) del artículo con ese factor —mismo comportamiento que hoy— y elimina `factor_estacional`. Prueba `UpgradeTemporadasTest` (H2) y caso en `PostgresRealTest`.
6. **Frontend**: gestión de temporadas (lista, alta, edición, baja) con el Design System; el modal de política estacional muestra el factor vigente que informa el backend y enlaza a la gestión. HTTP sólo en `api.ts`; operaciones con `run(accion, mensaje, ['politicas'])`.
7. **Docs**: `docs/reposicion-estacional.md`, README (tabla nueva, cadena de upgrades, endpoint), `docs/etapa-3.md` (ejemplo de política).

## Criterios de aceptación

- CA1: Con un reloj fijo dentro de la temporada del artículo, el pedido usa su factor; fuera de toda temporada usa factor 1.
- CA2: Una temporada 15/11 → 06/01 está vigente el 20/12 y el 03/01, y no el 10/01 ni el 14/11.
- CA3: La temporada del artículo prevalece sobre la de su categoría; sin temporada del artículo vigente, aplica la de la categoría.
- CA4: Guardar una temporada superpuesta con otra del mismo alcance devuelve 409 problem+json (`TEMPORADA_SUPERPUESTA`); fechas inexistentes o factor ≤ 0 devuelven 400.
- CA5: `upgrade-temporadas.sql` corre dos veces sin error y deja a Pan dulce con una temporada de todo el año ×1,5; la columna `factor_estacional` ya no existe.
- CA6: Compras puede crear, editar y borrar temporadas desde la UI; REPOSITOR recibe 403.
- CA7: `mvn -f backend/pom.xml verify`, `npm run build`, `npm test` y `npm run test:e2e` en verde.

## Riesgos

- PR #10 (abierto contra `develop`) toca `PoliticaReposicion`, `ReposicionEstacional`, `Solicitudes`, `Respuestas`, `MapeadorDto`, `RepositorioPoliticasJpa`, `CatalogForms.tsx`, `schema.sql` y `EstrategiasTest`: habrá conflictos de líneas al integrar el segundo de los dos.

## Cambio de alcance (pedido del usuario)

Una temporada abarca **uno o más artículos** (`stockIds`, tabla puente `temporada_articulo`) o una categoría. La superposición se controla por artículo en común. CA3 se lee como "la temporada que incluye al artículo prevalece sobre la de su categoría". Nuevo CA8: una temporada de varios artículos aplica su factor a cada uno; otra que comparta un artículo en fechas superpuestas devuelve 409 nombrando ese artículo.
