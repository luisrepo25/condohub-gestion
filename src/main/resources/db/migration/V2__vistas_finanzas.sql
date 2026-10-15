-- =====================================================================
-- CondoHub · Gestión · V2: vistas de lectura (estado de cuenta, morosidad)
-- Las usan las queries GraphQL, el agente (MCP) y los reportes por voz.
-- Son solo lectura: las reglas se aplican en el dominio, no aquí.
-- =====================================================================

-- F2: control de que las alícuotas activas suman 100 %
CREATE VIEW v_suma_alicuotas AS
SELECT SUM(alicuota)          AS suma,
       SUM(alicuota) = 100.00 AS cuadra
FROM unidad
WHERE activa;

-- F4: cargos vencidos e impagos
CREATE VIEW v_cargo_vencido AS
SELECT c.*,
       (current_date - c.fecha_vencimiento) AS dias_vencido
FROM cargo c
WHERE c.estado = 'PENDIENTE'
  AND c.fecha_vencimiento < current_date;

-- F1, F4, F7: estado de cuenta por unidad
CREATE VIEW v_estado_cuenta AS
SELECT u.id                                   AS unidad_id,
       u.codigo,
       u.bloque,
       u.propietario_titular_id,
       COALESCE(p.deuda_total, 0)             AS deuda_total,
       COALESCE(p.deuda_vencida, 0)           AS deuda_vencida,
       COALESCE(p.expensas_vencidas, 0)       AS expensas_vencidas,
       COALESCE(sf.saldo_a_favor, 0)          AS saldo_a_favor,
       COALESCE(p.deuda_vencida, 0) > 0       AS moroso
FROM unidad u
LEFT JOIN (
    SELECT unidad_id,
           SUM(saldo)                                                         AS deuda_total,
           SUM(saldo) FILTER (WHERE fecha_vencimiento < current_date)         AS deuda_vencida,
           COUNT(*)   FILTER (WHERE fecha_vencimiento < current_date
                                AND tipo = 'EXPENSA')                         AS expensas_vencidas
    FROM cargo
    WHERE estado = 'PENDIENTE'
    GROUP BY unidad_id
) p ON p.unidad_id = u.id
LEFT JOIN (
    -- F7: excedente de pagos confirmados que no se imputó a ningún cargo
    SELECT pg.unidad_id,
           SUM(pg.monto - COALESCE(i.imputado, 0)) AS saldo_a_favor
    FROM pago pg
    LEFT JOIN (SELECT pago_id, SUM(monto) AS imputado FROM imputacion_pago GROUP BY pago_id) i
           ON i.pago_id = pg.id
    WHERE pg.estado = 'CONFIRMADO'
    GROUP BY pg.unidad_id
) sf ON sf.unidad_id = u.id
WHERE u.activa;

-- Morosos (F4) con el dato que usa Reservas para R4
CREATE VIEW v_morosos AS
SELECT ec.*,
       ec.expensas_vencidas >= (SELECT valor::int FROM parametro
                                WHERE clave = 'reservas.expensas_vencidas_bloqueo') AS bloqueado_para_reservar
FROM v_estado_cuenta ec
WHERE ec.moroso;

-- Morosidad agregada por bloque y mes de vencimiento (reporte por voz de referencia)
CREATE VIEW v_morosidad_por_bloque AS
SELECT u.bloque,
       date_trunc('month', c.fecha_vencimiento)::date AS mes,
       COUNT(DISTINCT c.unidad_id)                    AS unidades_morosas,
       SUM(c.saldo)                                   AS monto_vencido
FROM v_cargo_vencido c
JOIN unidad u ON u.id = c.unidad_id
GROUP BY u.bloque, date_trunc('month', c.fecha_vencimiento);
