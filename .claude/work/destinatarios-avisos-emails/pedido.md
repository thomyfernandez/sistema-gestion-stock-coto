Destinatarios de avisos como listas de emails

Hoy el modal de Compras → Reposición → Política define reposición y también un único contacto/canal por artículo.
Separar:
1. Nueva sección Configuración → Destinatarios de avisos con listas de emails por categoría y una lista general.
2. El modal de política queda solo con parámetros de reposición (umbral, stock objetivo, tipo, etc.), sin contacto/canal.
3. AlertaStockBajo envía un mail por cada dirección configurada (resolución jerárquica o listas aplicables).
4. Dejar SMS/WhatsApp en segundo plano (enfocarse en emails).
5. Requiere migración db/upgrade-*.sql correspondiente y actualizar schema.sql y data.sql.

