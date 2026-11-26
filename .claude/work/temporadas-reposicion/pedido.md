# Pedido

Que la política de reposición `ESTACIONAL` sepa en qué temporada está, en vez de depender de un factor que Compras carga y quita a mano.

Hoy `ReposicionEstacional` calcula objetivo = ceil(stockObjetivo × factorEstacional) con un único factor guardado en la política (`politica_reposicion.factor_estacional`), que queda activo hasta que alguien lo cambie. El sistema no sabe en qué época del año está.

Propuesta base: temporadas con fecha desde/hasta (día y mes, se repiten cada año) y factor, por artículo o por categoría; fuera de toda temporada el factor es 1. La estrategia toma la fecha del `Clock` inyectado. Migrar el `factor_estacional` existente sin perder el comportamiento actual.
