-- EJECUCIÓN MANUAL, una vez, después de revisión y autorización.
-- No se carga desde Spring ni desde docker/init-db.sql.
-- Base esperada: oyl_productos. Conservar el bloque como una sola transacción.
-- Usar un cliente que detenga la ejecución ante errores (psql: ON_ERROR_STOP=1).
-- No contiene UPDATE, DELETE, renombrados ni eliminación de restricciones previas.
-- El bloqueo impide escrituras mientras se comprueban duplicados y se crean índices.
-- Si hay duplicados, índices con el mismo nombre o no se obtiene el bloqueo, se aborta.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $base$
BEGIN
    IF current_database() <> 'oyl_productos' THEN
        RAISE EXCEPTION 'Migración preparada para oyl_productos. Revisar la conexión antes de continuar.';
    END IF;
END;
$base$;

LOCK TABLE public.productos IN SHARE ROW EXCLUSIVE MODE;

DO $validacion$
BEGIN
    IF EXISTS (SELECT 1 FROM public.productos GROUP BY upper(codigo) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay valores duplicados de codigo al ignorar mayúsculas. Revisar los registros manualmente; no se ha corregido ni eliminado ninguno.';
    END IF;
END;
$validacion$;

-- Misma expresión UPPER que las consultas IgnoreCase de Spring Data.
-- No cambia la escritura original de los valores. Incluye productos eliminados.
CREATE UNIQUE INDEX oyl_productos_codigo_ci_uq ON public.productos (upper(codigo));
COMMIT;
