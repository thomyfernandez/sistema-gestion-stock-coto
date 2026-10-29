-- Datos de demostración ficticios: precios, cantidades y pedidos no representan información real de COTO.
-- Ejecutar UNA vez después de schema.sql sobre una base vacía.
INSERT INTO producto (id, nombre, descripcion, precio) VALUES
    (1, 'Arroz COTO largo fino 1 kg', 'Paquete de arroz seco, almacén', 1890.00),
    (2, 'Aceite de girasol COTO 900 ml', 'Botella de aceite, almacén', 3290.00),
    (3, 'Leche entera COTO 1 l', 'Leche refrigerada, frescos', 1590.00),
    (4, 'Bananas por kg', 'Unidad de inventario: un kilogramo entero', 2490.00),
    (5, 'Pan dulce COTO 500 g', 'Producto estacional de fin de año', 5490.00),
    (6, 'Combo Ahorro COTO', 'Dos paquetes de arroz y una botella de aceite', 6490.00);

INSERT INTO stock (id, tipo, stock_fisico, stock_exhibicion, stock_reservado) VALUES
    (1, 'SIMPLE', 120, 30, 10), -- Disponible 80.
    (2, 'SIMPLE', 55, 15, 5),   -- Disponible 35.
    (3, 'SIMPLE', 48, 24, 18),  -- Disponible 6: bajo umbral 10.
    (4, 'SIMPLE', 28, 8, 4),    -- Disponible 16.
    (5, 'SIMPLE', 40, 10, 0),   -- Disponible 30.
    (6, 'COMBO', NULL, NULL, NULL); -- min(80/2, 35/1) = 35 combos.

INSERT INTO linea_combo (combo_id, componente_id, cantidad_en_combo) VALUES
    (6, 1, 2),
    (6, 2, 1);

-- Historial completo de apertura: recepción, traslado a góndola y reservas.
-- Su aplicación sobre stock inicialmente cero reconstruye los contadores de arriba.
INSERT INTO movimiento_stock (id, stock_id, tipo, cantidad, fecha, motivo, usuario) VALUES
    (1, 1, 'RECEPCION', 120, TIMESTAMP WITH TIME ZONE '2026-10-15 08:00:00-03:00', 'Recepción inicial arroz', 'recepcion.demo'),
    (2, 1, 'TRASLADO_A_GONDOLA', 30, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 'Carga inicial góndola', 'pda.demo'),
    (3, 1, 'RESERVA_DIGITAL', 10, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 'Compra web de prueba', 'digital.demo'),
    (4, 2, 'RECEPCION', 55, TIMESTAMP WITH TIME ZONE '2026-10-15 08:00:00-03:00', 'Recepción inicial aceite', 'recepcion.demo'),
    (5, 2, 'TRASLADO_A_GONDOLA', 15, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 'Carga inicial góndola', 'pda.demo'),
    (6, 2, 'RESERVA_DIGITAL', 5, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 'Compra web de prueba', 'digital.demo'),
    (7, 3, 'RECEPCION', 48, TIMESTAMP WITH TIME ZONE '2026-10-15 08:00:00-03:00', 'Recepción inicial leche', 'recepcion.demo'),
    (8, 3, 'TRASLADO_A_GONDOLA', 24, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 'Carga inicial góndola', 'pda.demo'),
    (9, 3, 'RESERVA_DIGITAL', 18, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 'Compra web de prueba', 'digital.demo'),
    (10, 4, 'RECEPCION', 28, TIMESTAMP WITH TIME ZONE '2026-10-15 08:00:00-03:00', 'Recepción inicial bananas', 'recepcion.demo'),
    (11, 4, 'TRASLADO_A_GONDOLA', 8, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 'Carga inicial góndola', 'pda.demo'),
    (12, 4, 'RESERVA_DIGITAL', 4, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 'Compra web de prueba', 'digital.demo'),
    (13, 5, 'RECEPCION', 40, TIMESTAMP WITH TIME ZONE '2026-10-15 08:00:00-03:00', 'Recepción inicial pan dulce', 'recepcion.demo'),
    (14, 5, 'TRASLADO_A_GONDOLA', 10, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 'Carga inicial góndola', 'pda.demo');

-- Ejemplo por demanda para leche: ceil(12 * 3) - 6 = 30 unidades.
INSERT INTO pedido_reposicion (id, stock_id, fecha, cantidad, estado) VALUES
    (1, 3, TIMESTAMP WITH TIME ZONE '2026-10-15 09:01:00-03:00', 30, 'PENDIENTE');

INSERT INTO auditoria_stock
    (id, stock_id, movimiento_id, fecha, disponible_anterior, disponible_actual, detalle) VALUES
    (1, 1, 3, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 90, 80, 'Reserva digital: 10 unidades de arroz'),
    (2, 2, 6, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 40, 35, 'Reserva digital: 5 unidades de aceite'),
    (3, 3, 9, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 24, 6, 'Reserva digital: leche debajo del umbral 10'),
    (4, 4, 12, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', 20, 16, 'Reserva digital: 4 kg de bananas'),
    (5, 5, 14, TIMESTAMP WITH TIME ZONE '2026-10-15 08:10:00-03:00', 40, 30, 'Traslado de 10 unidades de pan dulce'),
    (6, 6, NULL, TIMESTAMP WITH TIME ZONE '2026-10-15 09:00:00-03:00', NULL, 35, 'Snapshot del combo derivado de sus componentes');

