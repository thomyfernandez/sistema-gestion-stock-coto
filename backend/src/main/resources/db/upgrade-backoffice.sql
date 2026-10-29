-- Aplicar una vez sobre el esquema de Etapas 2/3/4 anterior a estos ajustes.
-- Conserva productos, existencias, movimientos y políticas.
ALTER TABLE producto ADD COLUMN imagen_version VARCHAR(64);

-- Destinatarios compartidos por todos los artículos de la sucursal.
CREATE TABLE configuracion_avisos (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    canal VARCHAR(15) NOT NULL CHECK (canal IN ('EMAIL', 'SMS', 'WHATSAPP')),
    email VARCHAR(200) NOT NULL CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    telefono VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (canal = 'EMAIL' OR CHAR_LENGTH(TRIM(telefono)) > 0)
);

-- Bytes separados de producto para no cargar imágenes al consultar existencias.
CREATE TABLE imagen_producto (
    producto_id INTEGER PRIMARY KEY REFERENCES producto(id),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('image/png', 'image/jpeg')),
    contenido BYTEA NOT NULL CHECK (OCTET_LENGTH(contenido) BETWEEN 1 AND 2097152)
);

INSERT INTO configuracion_avisos (id, canal, email, telefono) VALUES (1, 'EMAIL', 'compras@coto.example', '+541100000000');
