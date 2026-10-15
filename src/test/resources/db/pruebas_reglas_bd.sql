-- Pruebas de las reglas que protege la base de Gestión.
-- Correr sobre una base con V1, V2 y los datos demo:
--   psql -d gestion -v ON_ERROR_STOP=1 -f pruebas_reglas_bd.sql
-- Cada bloque espera un error; si la operación pasa, la prueba falla.

CREATE OR REPLACE FUNCTION pg_temp.espera_error(p_regla TEXT, p_sql TEXT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE p_sql;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'OK %: %', p_regla, SQLERRM;
        RETURN;
    END;
    RAISE EXCEPTION 'FALLA %: la operación debió rechazarse', p_regla;
END $$;

BEGIN;
SELECT pg_temp.espera_error('F6 no editar monto',
  $q$UPDATE cargo SET monto = monto + 1 WHERE id = (SELECT id FROM cargo LIMIT 1)$q$);
SELECT pg_temp.espera_error('F6 no borrar cargo',
  $q$DELETE FROM cargo WHERE id = (SELECT id FROM cargo LIMIT 1)$q$);
SELECT pg_temp.espera_error('F3 una expensa por unidad y periodo',
  $q$INSERT INTO cargo (unidad_id, tipo, concepto, monto, saldo, fecha_emision, fecha_vencimiento, periodo_id)
     SELECT unidad_id, 'EXPENSA', 'dup', monto, monto, fecha_emision, fecha_vencimiento, periodo_id FROM cargo WHERE tipo='EXPENSA' LIMIT 1$q$);
SELECT pg_temp.espera_error('F7/F9 imputar pago pendiente',
  $q$INSERT INTO imputacion_pago (pago_id, cargo_id, monto)
     SELECT p.id, c.id, 1 FROM pago p, cargo c WHERE p.estado='PENDIENTE' LIMIT 1$q$);
SELECT pg_temp.espera_error('F8 recibo de pago pendiente',
  $q$INSERT INTO recibo (pago_id) SELECT id FROM pago WHERE estado='PENDIENTE' LIMIT 1$q$);
SELECT pg_temp.espera_error('F8 rechazo sin motivo',
  $q$UPDATE pago SET estado='RECHAZADO', revisado_en=now() WHERE estado='PENDIENTE'$q$);
SELECT pg_temp.espera_error('O3 idempotency_key repetida',
  $q$INSERT INTO pago (unidad_id, monto, fecha_pago, idempotency_key, registrado_por)
     SELECT unidad_id, 1, current_date, idempotency_key, 'x' FROM pago LIMIT 1$q$);
SELECT pg_temp.espera_error('F5 recargo confirmado sin cargo RECARGO válido',
  $q$INSERT INTO recargo_propuesto (unidad_id, cargo_origen_id, periodo, porcentaje, base, monto, estado, origen, resuelto_en, cargo_generado_id)
     SELECT c.unidad_id, c.id, DATE '2026-10-01', 2, c.saldo, 5.40, 'CONFIRMADO', 'AGENTE', now(), c.id
     FROM cargo c WHERE c.estado='PENDIENTE' LIMIT 1$q$);
SELECT pg_temp.espera_error('F10 monto negativo',
  $q$INSERT INTO egreso (proveedor_id, periodo, concepto, monto, fecha, registrado_por)
     SELECT id, DATE '2026-10-01', 'x', -5, current_date, 'x' FROM proveedor LIMIT 1$q$);
ROLLBACK;
