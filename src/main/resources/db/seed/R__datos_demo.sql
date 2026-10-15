-- =====================================================================
-- CondoHub · Gestión · Datos de demostración (SOLO perfil dev)
-- Flyway: spring.flyway.locations=classpath:db/migration,classpath:db/seed
-- 200 unidades: bloque A = 50 casas (alícuota 0.65 %), bloques B, C, D =
-- 150 departamentos (0.45 %) → suma 100 % (F2).
-- Expensas ago–oct 2026 con presupuesto 60 000 → 390 / 270 por unidad (F3).
-- Morosos: unidades 1–20 de cada bloque B/C deben sep y oct; 21–30 deben oct.
-- Usuarios demo con el sub fijo del realm de Keycloak (infra/keycloak).
-- =====================================================================

DO $$
DECLARE
    v_admin  CONSTANT VARCHAR := '00000000-0000-0000-0000-00000000a001';
    v_res    CONSTANT VARCHAR := '00000000-0000-0000-0000-00000000a002';
    r_unidad RECORD;
    r_cargo  RECORD;
    v_pago   UUID;
    v_periodo UUID;
    v_mes    DATE;
BEGIN
    IF EXISTS (SELECT 1 FROM unidad WHERE codigo = 'A-101') THEN
        RETURN;  -- ya sembrado
    END IF;

    -- Personas: un propietario por unidad
    INSERT INTO persona (nombre, documento, email, telefono)
    SELECT 'Propietario ' || b || '-' || (100 + n),
           'CI-' || b || lpad(n::text, 4, '0'),
           lower(b) || (100 + n) || '@vecinos.condohub.test',
           '7' || lpad((1000000 + ascii(b) * 1000 + n)::text, 7, '0')
    FROM unnest(ARRAY['A','B','C','D']) AS b, generate_series(1, 50) AS n;

    -- Residente demo = titular de A-101 con cuenta SSO
    UPDATE persona SET keycloak_sub = v_res, nombre = 'Residente Demo', email = 'residente@condohub.test'
    WHERE documento = 'CI-A0001';

    -- Unidades
    INSERT INTO unidad (codigo, bloque, tipo, superficie_m2, alicuota, propietario_titular_id)
    SELECT b || '-' || (100 + n), b,
           CASE WHEN b = 'A' THEN 'CASA' ELSE 'DEPARTAMENTO' END::tipo_unidad,
           CASE WHEN b = 'A' THEN 180 ELSE 95 END,
           CASE WHEN b = 'A' THEN 0.6500 ELSE 0.4500 END,
           p.id
    FROM unnest(ARRAY['A','B','C','D']) AS b
    CROSS JOIN generate_series(1, 50) AS n
    JOIN persona p ON p.documento = 'CI-' || b || lpad(n::text, 4, '0');

    -- Residentes (inquilinos) en algunos departamentos
    INSERT INTO persona (nombre, documento, email)
    SELECT 'Inquilino D-' || (100 + n), 'CI-INQ' || lpad(n::text, 4, '0'), 'inq' || n || '@vecinos.condohub.test'
    FROM generate_series(1, 20) n;
    INSERT INTO residente_unidad (unidad_id, persona_id, desde)
    SELECT u.id, p.id, DATE '2026-01-01'
    FROM generate_series(1, 20) n
    JOIN unidad u  ON u.codigo = 'D-' || (100 + n)
    JOIN persona p ON p.documento = 'CI-INQ' || lpad(n::text, 4, '0');

    -- Periodos y expensas (F3, F4)
    FOREACH v_mes IN ARRAY ARRAY[DATE '2026-08-01', DATE '2026-09-01', DATE '2026-10-01'] LOOP
        INSERT INTO periodo_expensas (periodo, presupuesto, estado, fecha_vencimiento, emitido_por, emitido_en)
        VALUES (v_mes, 60000.00, 'EMITIDO', v_mes + 9, v_admin, v_mes::timestamptz + interval '8 hours')
        RETURNING id INTO v_periodo;

        INSERT INTO cargo (unidad_id, tipo, concepto, monto, saldo, fecha_emision, fecha_vencimiento, periodo_id)
        SELECT u.id, 'EXPENSA', 'Expensa ' || to_char(v_mes, 'MM/YYYY'),
               round(60000.00 * u.alicuota / 100, 2), round(60000.00 * u.alicuota / 100, 2),
               v_mes, v_mes + 9, v_periodo
        FROM unidad u;
    END LOOP;

    -- Pagos confirmados de todo lo que no es moroso (F7, F8)
    FOR r_cargo IN
        SELECT c.*, u.codigo, u.bloque, (substr(u.codigo, 3)::int - 100) AS nro
        FROM cargo c JOIN unidad u ON u.id = c.unidad_id
        ORDER BY c.fecha_vencimiento
    LOOP
        CONTINUE WHEN r_cargo.bloque IN ('B','C') AND r_cargo.nro <= 20 AND r_cargo.fecha_emision >= DATE '2026-09-01';
        CONTINUE WHEN r_cargo.bloque IN ('B','C') AND r_cargo.nro BETWEEN 21 AND 30 AND r_cargo.fecha_emision >= DATE '2026-10-01';

        INSERT INTO pago (unidad_id, monto, fecha_pago, banco, nro_operacion, estado, idempotency_key,
                          registrado_por, registrado_en, revisado_por, revisado_en)
        VALUES (r_cargo.unidad_id, r_cargo.monto, r_cargo.fecha_vencimiento - 3, 'Banco Demo',
                'OP-' || substr(r_cargo.id::text, 1, 8), 'CONFIRMADO', gen_random_uuid(),
                'seed', r_cargo.fecha_vencimiento - 3, v_admin, r_cargo.fecha_vencimiento - 2)
        RETURNING id INTO v_pago;
        INSERT INTO imputacion_pago (pago_id, cargo_id, monto) VALUES (v_pago, r_cargo.id, r_cargo.monto);
        UPDATE cargo SET saldo = 0, estado = 'PAGADO' WHERE id = r_cargo.id;
        INSERT INTO recibo (pago_id, emitido_en) VALUES (v_pago, r_cargo.fecha_vencimiento - 2);
    END LOOP;

    -- Dos pagos pendientes de conciliación (demo de la consola, F8)
    FOR r_unidad IN SELECT id, codigo FROM unidad WHERE codigo IN ('B-121', 'C-122') LOOP
        INSERT INTO pago (unidad_id, monto, fecha_pago, banco, nro_operacion, estado, idempotency_key,
                          registrado_por, datos_ocr)
        VALUES (r_unidad.id, 270.00, DATE '2026-10-13', 'Banco Demo', 'OP-PEND-' || r_unidad.codigo,
                'PENDIENTE', gen_random_uuid(), 'seed',
                jsonb_build_object('monto', '270.00', 'fecha', '2026-10-13', 'banco', 'Banco Demo', 'confianza', 0.91));
    END LOOP;

    -- Proveedores y egresos (F11)
    INSERT INTO proveedor (nombre, documento, rubro, contacto) VALUES
        ('Seguridad Andina SRL', 'NIT-1001', 'Seguridad',     'contacto@seguridad.test'),
        ('Limpieza Total',       'NIT-1002', 'Limpieza',      'contacto@limpieza.test'),
        ('Jardines del Valle',   'NIT-1003', 'Jardinería',    'contacto@jardines.test'),
        ('Servicios Eléctricos', 'NIT-1004', 'Energía',       'contacto@energia.test');
    INSERT INTO egreso (proveedor_id, periodo, concepto, monto, fecha, registrado_por)
    SELECT pr.id, m, 'Servicio ' || pr.rubro || ' ' || to_char(m, 'MM/YYYY'),
           CASE pr.rubro WHEN 'Seguridad' THEN 28000 WHEN 'Limpieza' THEN 14000
                         WHEN 'Jardinería' THEN 6000 ELSE 9000 END,
           m + 25, v_admin
    FROM proveedor pr, unnest(ARRAY[DATE '2026-08-01', DATE '2026-09-01']) AS m;
END $$;
