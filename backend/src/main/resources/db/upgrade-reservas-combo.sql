-- Actualización de esquema: reservas virtuales de combos.
-- El combo registra cuántas unidades reservó sobre sus componentes simples, para que una
-- cancelación o despacho del combo no libere reservas hechas sobre el artículo suelto u otro combo.
-- Idempotente: puede ejecutarse más de una vez. Las bases existentes arrancan con 0.

ALTER TABLE stock ADD COLUMN IF NOT EXISTS stock_reservado_virtual INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stock DROP CONSTRAINT IF EXISTS ck_stock_reservado_virtual;
ALTER TABLE stock ADD CONSTRAINT ck_stock_reservado_virtual
    CHECK (stock_reservado_virtual >= 0 AND (tipo = 'COMBO' OR stock_reservado_virtual = 0));
