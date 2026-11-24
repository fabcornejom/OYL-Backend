-- EJECUCIÓN MANUAL una vez, después de revisión y autorización.
-- Requisito para desplegar el nuevo flujo de movimientos. No se ejecuta al arrancar.
-- Cliente con detención ante errores (psql ON_ERROR_STOP=1). No modifica existencias.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
DO $base$
BEGIN
    IF current_database() <> 'oyl_productos' THEN
        RAISE EXCEPTION 'Migración preparada para oyl_productos. Revisar la conexión.';
    END IF;
END;
$base$;
CREATE TABLE movimientos_stock (
    actor_id BIGINT NOT NULL CHECK (actor_id > 0), clave UUID NOT NULL,
    producto_id BIGINT NOT NULL CHECK (producto_id > 0),
    cambio INTEGER NOT NULL CHECK (cambio <> 0 AND cambio > -2147483648),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTA','APLICADA','RECHAZADA')),
    stock_anterior INTEGER, stock_resultante INTEGER, mensaje VARCHAR(300),
    fecha TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),
    PRIMARY KEY (actor_id,clave),
    CHECK ((estado = 'APLICADA' AND stock_anterior IS NOT NULL AND stock_resultante IS NOT NULL
        AND stock_anterior >= 0 AND stock_resultante >= 0
        AND stock_resultante::BIGINT = stock_anterior::BIGINT + cambio::BIGINT)
        OR (estado IN ('ABIERTA','RECHAZADA') AND stock_anterior IS NULL AND stock_resultante IS NULL))
);
-- Sin FK: conservar el comprobante histórico aunque una cuenta o producto se elimine.
COMMIT;
