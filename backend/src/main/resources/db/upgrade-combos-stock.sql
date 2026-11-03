-- Actualización de esquema: armado y desarmado de combos con stock propio.
-- Permite que los combos tengan contadores propios de stock físico, exhibición y reservado,
-- y habilita los movimientos ARMADO_COMBO y DESARMADO_COMBO.

ALTER TABLE stock DROP CONSTRAINT IF EXISTS ck_stock_segun_tipo;
ALTER TABLE stock ADD CONSTRAINT ck_stock_segun_tipo CHECK (
    (tipo = 'SIMPLE'
        AND stock_fisico IS NOT NULL AND stock_exhibicion IS NOT NULL AND stock_reservado IS NOT NULL
        AND stock_fisico >= 0 AND stock_exhibicion >= 0 AND stock_reservado >= 0
        AND CAST(stock_exhibicion AS BIGINT) + stock_reservado <= stock_fisico)
    OR
    (tipo = 'COMBO'
        AND (
            (stock_fisico IS NULL AND stock_exhibicion IS NULL AND stock_reservado IS NULL)
            OR
            (stock_fisico IS NOT NULL AND stock_exhibicion IS NOT NULL AND stock_reservado IS NOT NULL
                AND stock_fisico >= 0 AND stock_exhibicion >= 0 AND stock_reservado >= 0
                AND CAST(stock_exhibicion AS BIGINT) + stock_reservado <= stock_fisico)
        ))
);

ALTER TABLE movimiento_stock DROP CONSTRAINT IF EXISTS movimiento_stock_tipo_check;
ALTER TABLE movimiento_stock ADD CONSTRAINT movimiento_stock_tipo_check CHECK (tipo IN (
    'RECEPCION', 'TRASLADO_A_GONDOLA', 'RETORNO_A_DEPOSITO', 'DEVOLUCION',
    'MERMA_DEPOSITO', 'MERMA_GONDOLA', 'RESERVA_DIGITAL', 'CANCELACION_RESERVA', 'DESPACHO_DIGITAL',
    'ARMADO_COMBO', 'DESARMADO_COMBO'
));

