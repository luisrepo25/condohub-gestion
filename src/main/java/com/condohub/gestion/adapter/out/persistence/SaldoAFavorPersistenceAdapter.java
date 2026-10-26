package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.SaldoAFavorRepository;
import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.model.SaldoAFavor;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SaldoAFavorPersistenceAdapter implements SaldoAFavorRepository {

    private final JdbcTemplate jdbc;

    SaldoAFavorPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, List<SaldoAFavor>> saldosAFavorPorUnidad() {
        Map<UUID, List<SaldoAFavor>> porUnidad = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT pg.id, pg.unidad_id, pg.monto - COALESCE(i.imputado, 0) AS disponible
                  FROM pago pg
                  LEFT JOIN (SELECT pago_id, SUM(monto) AS imputado FROM imputacion_pago GROUP BY pago_id) i
                         ON i.pago_id = pg.id
                 WHERE pg.estado = 'CONFIRMADO' AND pg.monto - COALESCE(i.imputado, 0) > 0
                 ORDER BY pg.unidad_id, pg.revisado_en, pg.registrado_en
                   FOR UPDATE OF pg
                """,
                rs -> {
                    porUnidad.computeIfAbsent(rs.getObject("unidad_id", UUID.class), u -> new ArrayList<>())
                            .add(new SaldoAFavor(
                                    rs.getObject("id", UUID.class),
                                    Dinero.de(rs.getObject("disponible", BigDecimal.class))));
                });
        return porUnidad;
    }

    @Override
    public List<SaldoAFavor> saldosAFavorDe(UUID unidadId) {
        return jdbc.query(
                """
                SELECT pg.id, pg.monto - COALESCE(i.imputado, 0) AS disponible
                  FROM pago pg
                  LEFT JOIN (SELECT pago_id, SUM(monto) AS imputado FROM imputacion_pago GROUP BY pago_id) i
                         ON i.pago_id = pg.id
                 WHERE pg.unidad_id = ? AND pg.estado = 'CONFIRMADO' AND pg.monto - COALESCE(i.imputado, 0) > 0
                 ORDER BY pg.revisado_en, pg.registrado_en
                   FOR UPDATE OF pg
                """,
                (rs, i) -> new SaldoAFavor(
                        rs.getObject("id", UUID.class), Dinero.de(rs.getObject("disponible", BigDecimal.class))),
                unidadId);
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
    public void imputar(List<ImputacionDePago> imputaciones) {
        for (var imputacion : imputaciones) {
            BigDecimal monto = imputacion.monto().monto();
            // Un mismo pago puede volver a aplicarse a un cargo que ya pagó en parte: se suma a la fila existente.
            jdbc.update(
                    """
                    INSERT INTO imputacion_pago (pago_id, cargo_id, monto) VALUES (?, ?, ?)
                    ON CONFLICT (pago_id, cargo_id) DO UPDATE SET monto = imputacion_pago.monto + EXCLUDED.monto
                    """,
                    imputacion.pagoId(),
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
    }
}
