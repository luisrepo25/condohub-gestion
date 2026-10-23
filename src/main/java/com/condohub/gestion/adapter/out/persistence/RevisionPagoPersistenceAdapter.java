package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.RevisionPagoRepository;
import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.PagoPorRevisar;
import com.condohub.gestion.domain.model.ResultadoImputacion;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RevisionPagoPersistenceAdapter implements RevisionPagoRepository {

    private final JdbcTemplate jdbc;

    RevisionPagoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PagoPorRevisar> porIdParaRevisar(UUID pagoId) {
        return jdbc.query(
                        "SELECT id, unidad_id, monto, estado FROM pago WHERE id = ? FOR UPDATE",
                        (rs, i) -> new PagoPorRevisar(
                                rs.getObject("id", UUID.class),
                                rs.getObject("unidad_id", UUID.class),
                                Dinero.de(rs.getObject("monto", BigDecimal.class)),
                                EstadoPago.valueOf(rs.getString("estado"))),
                        pagoId)
                .stream()
                .findFirst();
    }

    @Override
    public List<CargoPendiente> cargosPendientes(UUID unidadId) {
        return jdbc.query(
                """
                SELECT id, saldo, fecha_vencimiento, creado_en FROM cargo
                 WHERE unidad_id = ? AND estado = 'PENDIENTE'
                 ORDER BY fecha_vencimiento, creado_en
                 FOR UPDATE
                """,
                (rs, i) -> new CargoPendiente(
                        rs.getObject("id", UUID.class),
                        Dinero.de(rs.getObject("saldo", BigDecimal.class)),
                        rs.getObject("fecha_vencimiento", LocalDate.class),
                        rs.getObject("creado_en", Timestamp.class).toInstant()),
                unidadId);
    }

    @Override
    public long confirmar(UUID pagoId, String adminSub, ResultadoImputacion resultado) {
        // El pago va primero: los triggers solo aceptan imputaciones y recibos de pagos CONFIRMADOS.
        int filas = jdbc.update(
                """
                UPDATE pago SET estado = 'CONFIRMADO', revisado_por = ?, revisado_en = now(), version = version + 1
                 WHERE id = ? AND estado = 'PENDIENTE'
                """,
                adminSub,
                pagoId);
        if (filas != 1) {
            throw new IllegalStateException("El pago " + pagoId + " ya no está PENDIENTE");
        }
        for (var imputacion : resultado.imputaciones()) {
            BigDecimal monto = imputacion.monto().monto();
            jdbc.update(
                    "INSERT INTO imputacion_pago (pago_id, cargo_id, monto) VALUES (?, ?, ?)",
                    pagoId,
                    imputacion.cargoId(),
                    monto);
            jdbc.update(
                    """
                    UPDATE cargo SET saldo = saldo - ?,
                           estado = CASE WHEN saldo - ? = 0 THEN 'PAGADO'::estado_cargo ELSE estado END
                     WHERE id = ?
                    """,
                    monto,
                    monto,
                    imputacion.cargoId());
        }
        Long numero = jdbc.queryForObject(
                "INSERT INTO recibo (pago_id) VALUES (?) RETURNING numero", Long.class, pagoId);
        return numero == null ? 0 : numero;
    }

    @Override
    public void rechazar(UUID pagoId, String adminSub, String motivo) {
        int filas = jdbc.update(
                """
                UPDATE pago SET estado = 'RECHAZADO', motivo_rechazo = ?, revisado_por = ?, revisado_en = now(),
                       version = version + 1
                 WHERE id = ? AND estado = 'PENDIENTE'
                """,
                motivo,
                adminSub,
                pagoId);
        if (filas != 1) {
            throw new IllegalStateException("El pago " + pagoId + " ya no está PENDIENTE");
        }
    }

    @Override
    public Pago pago(UUID pagoId) {
        return jdbc.queryForObject(
                "SELECT id, unidad_id, monto, fecha_pago, estado FROM pago WHERE id = ?",
                (rs, i) -> new Pago(
                        rs.getObject("id", UUID.class),
                        rs.getObject("unidad_id", UUID.class),
                        Dinero.de(rs.getObject("monto", BigDecimal.class)),
                        rs.getObject("fecha_pago", LocalDate.class),
                        EstadoPago.valueOf(rs.getString("estado"))),
                pagoId);
    }
}
