CREATE TABLE IF NOT EXISTS compras_stock (
 operacion VARCHAR(100) PRIMARY KEY, contenido TEXT NOT NULL,
 estado VARCHAR(20) NOT NULL, mensaje VARCHAR(300), total BIGINT,
 fecha TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);
CREATE TABLE IF NOT EXISTS compras_stock_detalles (
 operacion VARCHAR(100) NOT NULL REFERENCES compras_stock(operacion),
 producto_id BIGINT NOT NULL, nombre VARCHAR(100) NOT NULL,
 precio INTEGER NOT NULL, cantidad INTEGER NOT NULL,
 PRIMARY KEY (operacion, producto_id)
);

CREATE TABLE IF NOT EXISTS devoluciones_stock (
 operacion VARCHAR(80) PRIMARY KEY, contenido TEXT NOT NULL,
 estado VARCHAR(20) NOT NULL, mensaje VARCHAR(300),
 fecha TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
);

-- Una fotografía vigente por producto; el archivo no se incluye en el listado JSON.
CREATE TABLE IF NOT EXISTS fotos_producto (
 producto_id BIGINT PRIMARY KEY REFERENCES productos(id) ON DELETE CASCADE,
 version VARCHAR(36) NOT NULL, contenido BYTEA NOT NULL,
 CONSTRAINT foto_producto_tamano CHECK (octet_length(contenido) BETWEEN 1 AND 2097152)
);

-- Los registros existentes siguen visibles. El código y el stock no se eliminan.
ALTER TABLE productos ADD COLUMN IF NOT EXISTS eliminado BOOLEAN NOT NULL DEFAULT FALSE;
