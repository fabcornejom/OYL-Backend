-- EJECUCIÓN MANUAL, una vez, después de revisión y autorización.
-- No se carga desde Spring ni desde docker/init-db.sql.
-- Base esperada: oyl_usuarios. Conservar el bloque como una sola transacción.
-- Usar un cliente que detenga la ejecución ante errores (psql: ON_ERROR_STOP=1).
-- No contiene UPDATE, DELETE, renombrados ni eliminación de restricciones previas.
-- El bloqueo impide escrituras mientras se comprueban duplicados y se crean índices.
-- Si hay duplicados, índices con el mismo nombre o no se obtiene el bloqueo, se aborta.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $base$
BEGIN
    IF current_database() <> 'oyl_usuarios' THEN
        RAISE EXCEPTION 'Migración preparada para oyl_usuarios. Revisar la conexión antes de continuar.';
    END IF;
END;
$base$;

LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;

DO $validacion$
BEGIN
    IF EXISTS (SELECT 1 FROM public.usuarios GROUP BY upper(alias) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay valores duplicados de alias al ignorar mayúsculas. Revisar los registros manualmente; no se ha corregido ni eliminado ninguno.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.usuarios GROUP BY upper(correo) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay valores duplicados de correo al ignorar mayúsculas. Revisar los registros manualmente; no se ha corregido ni eliminado ninguno.';
    END IF;
END;
$validacion$;

-- Misma expresión UPPER que las consultas IgnoreCase de Spring Data.
-- No cambia la escritura original de alias ni correos.
CREATE UNIQUE INDEX oyl_usuarios_alias_ci_uq ON public.usuarios (upper(alias));
CREATE UNIQUE INDEX oyl_usuarios_correo_ci_uq ON public.usuarios (upper(correo));
COMMIT;
