-- =====================================================================
-- CondoHub · Gestión empresarial (contexto Finanzas) · PostgreSQL 16
-- V1: esquema base. Reglas: F1–F11 (docs/negocio/reglas-de-negocio.md)
-- Base exclusiva de este servicio: ningún otro servicio la lee ni escribe.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
CREATE TYPE tipo_unidad      AS ENUM ('CASA', 'DEPARTAMENTO');
CREATE TYPE estado_periodo   AS ENUM ('BORRADOR', 'EMITIDO');
CREATE TYPE tipo_cargo       AS ENUM ('EXPENSA', 'RECARGO', 'USO_AREA', 'GARANTIA', 'AJUSTE');
CREATE TYPE estado_cargo     AS ENUM ('PENDIENTE', 'PAGADO', 'ANULADO');
CREATE TYPE estado_pago      AS ENUM ('PENDIENTE', 'CONFIRMADO', 'RECHAZADO');
CREATE TYPE estado_recargo   AS ENUM ('PROPUESTO', 'CONFIRMADO', 'RECHAZADO');
CREATE TYPE origen_propuesta AS ENUM ('SISTEMA', 'AGENTE', 'ADMIN');

-- ---------------------------------------------------------------------
-- Parámetros de negocio (nunca hardcodeados en el código)
-- ---------------------------------------------------------------------
CREATE TABLE parametro (
    clave          VARCHAR(60)  PRIMARY KEY,
    valor          VARCHAR(200) NOT NULL,
    descripcion    VARCHAR(300) NOT NULL,
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO parametro (clave, valor, descripcion) VALUES
  ('expensas.dia_emision',             '1',    'F3: día del mes en que se emiten las expensas'),
  ('expensas.dia_vencimiento',         '10',   'F4: día del mes en que vence la expensa'),
  ('recargo.porcentaje_mensual',       '2.00', 'F5: porcentaje mensual simple sobre saldo vencido'),
  ('reservas.expensas_vencidas_bloqueo','2',   'R4: expensas vencidas a partir de las cuales no se puede reservar'),
  ('moneda.codigo',                    'BOB',  'F10: moneda única del condominio (configurable)');

-- ---------------------------------------------------------------------
-- Personas y unidades (agregado Unidad · F1, F2)
-- ---------------------------------------------------------------------
CREATE TABLE persona (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_sub  VARCHAR(64)  UNIQUE,                 -- enlace con el SSO; null si aún no tiene cuenta
    nombre        VARCHAR(120) NOT NULL,
    documento     VARCHAR(30)  NOT NULL UNIQUE,
    email         VARCHAR(160) UNIQUE,
    telefono      VARCHAR(30),
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE unidad (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo                 VARCHAR(10)   NOT NULL UNIQUE,      -- ej. A-101
    bloque                 VARCHAR(5)    NOT NULL,
    tipo                   tipo_unidad   NOT NULL,
    superficie_m2          NUMERIC(8,2)  NOT NULL CHECK (superficie_m2 > 0),
    alicuota               NUMERIC(7,4)  NOT NULL CHECK (alicuota > 0 AND alicuota <= 100),  -- porcentaje
    propietario_titular_id UUID          NOT NULL REFERENCES persona(id),                  -- F1: exactamente uno
    activa                 BOOLEAN       NOT NULL DEFAULT true,
    version                INTEGER       NOT NULL DEFAULT 0,                               -- bloqueo optimista
    creada_en              TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_unidad_bloque ON unidad (bloque);

-- F1: 0..n residentes por unidad, con vigencia
CREATE TABLE residente_unidad (
    unidad_id  UUID NOT NULL REFERENCES unidad(id),
    persona_id UUID NOT NULL REFERENCES persona(id),
    desde      DATE NOT NULL DEFAULT current_date,
    hasta      DATE,
    PRIMARY KEY (unidad_id, persona_id, desde),
    CHECK (hasta IS NULL OR hasta >= desde)
);
CREATE INDEX ix_residente_persona ON residente_unidad (persona_id);

-- ---------------------------------------------------------------------
-- Periodo de expensas (agregado PeriodoExpensas · F2, F3)
-- ---------------------------------------------------------------------
CREATE TABLE periodo_expensas (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    periodo           DATE           NOT NULL UNIQUE CHECK (EXTRACT(DAY FROM periodo) = 1),  -- primer día del mes
    presupuesto       NUMERIC(12,2)  NOT NULL CHECK (presupuesto > 0),
    estado            estado_periodo NOT NULL DEFAULT 'BORRADOR',
    fecha_vencimiento DATE           NOT NULL,
    emitido_por       VARCHAR(64),                                  -- keycloak sub del ADMIN
    emitido_en        TIMESTAMPTZ,
    CHECK (fecha_vencimiento > periodo),
    CHECK ((estado = 'EMITIDO') = (emitido_en IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- Cargos (agregado CuentaUnidad · F4, F6)
-- monto, tipo y unidad son inmutables (trigger abajo); solo saldo y estado cambian.
-- ---------------------------------------------------------------------
CREATE TABLE cargo (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    unidad_id          UUID          NOT NULL REFERENCES unidad(id),
    tipo               tipo_cargo    NOT NULL,
    concepto           VARCHAR(200)  NOT NULL,
    monto              NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    saldo              NUMERIC(12,2) NOT NULL,
    estado             estado_cargo  NOT NULL DEFAULT 'PENDIENTE',
    fecha_emision      DATE          NOT NULL DEFAULT current_date,
    fecha_vencimiento  DATE          NOT NULL,
    periodo_id         UUID          REFERENCES periodo_expensas(id),  -- solo EXPENSA
    cargo_origen_id    UUID          REFERENCES cargo(id),             -- RECARGO: cargo vencido que lo genera
    referencia_externa VARCHAR(80),                                    -- ej. reservaId (saga paso 2)
    creado_en          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (saldo >= 0 AND saldo <= monto),
    CHECK (fecha_vencimiento >= fecha_emision),
    CHECK ((tipo = 'EXPENSA') = (periodo_id IS NOT NULL)),
    CHECK (tipo <> 'RECARGO' OR cargo_origen_id IS NOT NULL),
    CHECK (estado <> 'PAGADO' OR saldo = 0),
    CHECK (estado <> 'PENDIENTE' OR saldo > 0)
);
-- F3: una expensa por unidad y periodo
CREATE UNIQUE INDEX ux_cargo_expensa_periodo ON cargo (unidad_id, periodo_id) WHERE tipo = 'EXPENSA';
-- S5: la saga no puede crear dos veces el mismo cargo de una reserva
CREATE UNIQUE INDEX ux_cargo_referencia ON cargo (referencia_externa, tipo) WHERE referencia_externa IS NOT NULL;
-- F7: buscar cargos pendientes en orden FIFO
CREATE INDEX ix_cargo_pendiente_fifo ON cargo (unidad_id, fecha_vencimiento, creado_en) WHERE estado = 'PENDIENTE';

-- F6: los cargos no se borran ni cambian lo que se cobró
CREATE FUNCTION fn_cargo_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'F6: los cargos no se borran (cargo %)', OLD.id USING ERRCODE = 'P0001';
    END IF;
    IF NEW.unidad_id <> OLD.unidad_id OR NEW.tipo <> OLD.tipo OR NEW.monto <> OLD.monto
       OR NEW.concepto <> OLD.concepto OR NEW.fecha_emision <> OLD.fecha_emision
       OR NEW.fecha_vencimiento <> OLD.fecha_vencimiento THEN
        RAISE EXCEPTION 'F6: un cargo no se edita; use nota de crédito o cargo de ajuste (cargo %)', OLD.id
              USING ERRCODE = 'P0001';
    END IF;
    IF OLD.estado = 'ANULADO' THEN
        RAISE EXCEPTION 'F6: un cargo anulado no cambia (cargo %)', OLD.id USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_cargo_inmutable BEFORE UPDATE OR DELETE ON cargo
    FOR EACH ROW EXECUTE FUNCTION fn_cargo_inmutable();

-- F6: correcciones y anulaciones (también compensación de la saga)
CREATE TABLE nota_credito (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    cargo_id           UUID          NOT NULL REFERENCES cargo(id),
    monto              NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    motivo             VARCHAR(300)  NOT NULL,
    referencia_externa VARCHAR(80)   UNIQUE,               -- idempotencia de la compensación
    creada_por         VARCHAR(64)   NOT NULL,
    creada_en          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_nota_credito_cargo ON nota_credito (cargo_id);

-- ---------------------------------------------------------------------
-- Pagos (agregado Pago · F7, F8, F9)
-- ---------------------------------------------------------------------
CREATE TABLE pago (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    unidad_id        UUID          NOT NULL REFERENCES unidad(id),
    monto            NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    fecha_pago       DATE          NOT NULL,              -- fecha del comprobante
    banco            VARCHAR(60),
    nro_operacion    VARCHAR(60),
    comprobante_key  VARCHAR(200),                        -- objeto en MinIO
    datos_ocr        JSONB,                               -- lo que leyó el OCR local (auditoría)
    estado           estado_pago   NOT NULL DEFAULT 'PENDIENTE',   -- F9: siempre nace pendiente
    motivo_rechazo   VARCHAR(300),
    idempotency_key  UUID          NOT NULL UNIQUE,       -- O1–O3: un reintento offline no duplica
    registrado_por   VARCHAR(64)   NOT NULL,
    registrado_en    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    hora_dispositivo TIMESTAMPTZ,
    revisado_por     VARCHAR(64),
    revisado_en      TIMESTAMPTZ,
    version          INTEGER       NOT NULL DEFAULT 0,
    CHECK (estado <> 'RECHAZADO' OR motivo_rechazo IS NOT NULL),                 -- F8
    CHECK ((estado = 'PENDIENTE') = (revisado_en IS NULL))
);
CREATE INDEX ix_pago_pendiente ON pago (registrado_en) WHERE estado = 'PENDIENTE';
CREATE INDEX ix_pago_unidad ON pago (unidad_id, fecha_pago);
-- evita registrar dos veces el mismo comprobante bancario
CREATE UNIQUE INDEX ux_pago_operacion ON pago (banco, nro_operacion) WHERE nro_operacion IS NOT NULL AND estado <> 'RECHAZADO';

-- F7: cómo se repartió un pago confirmado entre cargos (FIFO lo decide el dominio)
CREATE TABLE imputacion_pago (
    id        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pago_id   UUID          NOT NULL REFERENCES pago(id),
    cargo_id  UUID          NOT NULL REFERENCES cargo(id),
    monto     NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    creada_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (pago_id, cargo_id)
);
CREATE INDEX ix_imputacion_cargo ON imputacion_pago (cargo_id);

-- Solo se imputan pagos confirmados
CREATE FUNCTION fn_imputacion_solo_confirmado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT estado FROM pago WHERE id = NEW.pago_id) <> 'CONFIRMADO' THEN
        RAISE EXCEPTION 'F7/F9: solo se imputa un pago CONFIRMADO (pago %)', NEW.pago_id USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_imputacion_solo_confirmado BEFORE INSERT ON imputacion_pago
    FOR EACH ROW EXECUTE FUNCTION fn_imputacion_solo_confirmado();

-- F8: recibo solo de pagos confirmados, numeración correlativa
CREATE SEQUENCE seq_recibo START 1;
CREATE TABLE recibo (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    numero     BIGINT      NOT NULL UNIQUE DEFAULT nextval('seq_recibo'),
    pago_id    UUID        NOT NULL UNIQUE REFERENCES pago(id),
    archivo_key VARCHAR(200),
    emitido_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION fn_recibo_solo_confirmado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT estado FROM pago WHERE id = NEW.pago_id) <> 'CONFIRMADO' THEN
        RAISE EXCEPTION 'F8: el recibo se emite solo al confirmar el pago (pago %)', NEW.pago_id USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_recibo_solo_confirmado BEFORE INSERT ON recibo
    FOR EACH ROW EXECUTE FUNCTION fn_recibo_solo_confirmado();

-- ---------------------------------------------------------------------
-- Recargos propuestos (F5, G2): nada se cobra sin confirmación del ADMIN
-- ---------------------------------------------------------------------
CREATE TABLE recargo_propuesto (
    id                UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    unidad_id         UUID             NOT NULL REFERENCES unidad(id),
    cargo_origen_id   UUID             NOT NULL REFERENCES cargo(id),
    periodo           DATE             NOT NULL CHECK (EXTRACT(DAY FROM periodo) = 1),
    porcentaje        NUMERIC(5,2)     NOT NULL CHECK (porcentaje > 0),
    base              NUMERIC(12,2)    NOT NULL CHECK (base > 0),
    monto             NUMERIC(12,2)    NOT NULL CHECK (monto > 0),
    estado            estado_recargo   NOT NULL DEFAULT 'PROPUESTO',
    origen            origen_propuesta NOT NULL,
    mision_id         VARCHAR(40),                     -- id de la misión del agente (MongoDB)
    motivo            VARCHAR(300),
    propuesto_en      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    resuelto_por      VARCHAR(64),                     -- keycloak sub del ADMIN
    resuelto_en       TIMESTAMPTZ,
    cargo_generado_id UUID             UNIQUE REFERENCES cargo(id),
    CHECK ((estado = 'PROPUESTO') = (resuelto_en IS NULL)),
    CHECK ((estado = 'CONFIRMADO') = (cargo_generado_id IS NOT NULL)),
    UNIQUE (cargo_origen_id, periodo)                  -- un recargo por cargo vencido y mes
);

-- F5: un cargo RECARGO solo existe si viene de una propuesta confirmada
CREATE FUNCTION fn_recargo_confirmado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.cargo_generado_id IS NOT NULL AND
       (SELECT tipo FROM cargo WHERE id = NEW.cargo_generado_id) <> 'RECARGO' THEN
        RAISE EXCEPTION 'F5: el cargo generado debe ser de tipo RECARGO' USING ERRCODE = 'P0001';
    END IF;
    IF NEW.estado = 'CONFIRMADO' AND NEW.resuelto_por IS NULL THEN
        RAISE EXCEPTION 'F5: un recargo se confirma solo por un ADMIN identificado' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_recargo_confirmado BEFORE INSERT OR UPDATE ON recargo_propuesto
    FOR EACH ROW EXECUTE FUNCTION fn_recargo_confirmado();

-- ---------------------------------------------------------------------
-- Proveedores y egresos (F11, sin flujo de aprobación)
-- ---------------------------------------------------------------------
CREATE TABLE proveedor (
    id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre    VARCHAR(120) NOT NULL,
    documento VARCHAR(30)  UNIQUE,
    rubro     VARCHAR(60)  NOT NULL,
    contacto  VARCHAR(160),
    activo    BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE egreso (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    proveedor_id    UUID          NOT NULL REFERENCES proveedor(id),
    periodo         DATE          NOT NULL CHECK (EXTRACT(DAY FROM periodo) = 1),
    concepto        VARCHAR(200)  NOT NULL,
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    fecha           DATE          NOT NULL,
    comprobante_key VARCHAR(200),
    registrado_por  VARCHAR(64)   NOT NULL,
    registrado_en   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_egreso_periodo ON egreso (periodo);

-- ---------------------------------------------------------------------
-- Mensajería confiable (S4 Outbox, S5 consumidores idempotentes)
-- ---------------------------------------------------------------------
CREATE TABLE outbox (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),   -- = eventId del sobre
    aggregate_type VARCHAR(40)  NOT NULL,
    aggregate_id   VARCHAR(80)  NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    routing_key    VARCHAR(100) NOT NULL,                                -- ej. finanzas.CargoAplicado
    saga_id        UUID,
    payload        JSONB        NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    intentos       INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pendiente ON outbox (occurred_at) WHERE published_at IS NULL;

CREATE TABLE evento_procesado (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(60) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
