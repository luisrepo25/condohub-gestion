package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.CargoReservaRepository;
import com.condohub.gestion.domain.model.CargoAplicadoDeReserva;
import com.condohub.gestion.domain.model.CargoDeReserva;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cargos de la saga de reserva sobre {@code cargo} ({@code referencia_externa} = reservaId), {@code nota_credito} y
 * {@code evento_procesado}. La base protege la idempotencia: un cargo de cada tipo por reserva
 * ({@code ux_cargo_referencia}) y una nota de crédito por referencia.
 */
@Component
public class CargoReservaPersistenceAdapter implements CargoReservaRepository {

    /** Quién figura como autor de las notas de crédito de la saga (no hay un ADMIN detrás: lo pide Reservas). */
    static final String AUTOR = "saga-reservas";

    private final JdbcTemplate jdbc;

    CargoReservaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean marcarProcesado(UUID eventId, String tipo) {
        return jdbc.update(
                        "INSERT INTO evento_procesado (event_id, event_type) VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING",
                        eventId,
                        tipo)
                == 1;
    }

    @Override
    public boolean existeUnidad(UUID unidadId) {
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM unidad WHERE id = ?", Integer.class, unidadId);
        return cantidad != null && cantidad > 0;
    }

    @Override
    public List<CargoAplicadoDeReserva> deLaReserva(UUID reservaId) {
        return jdbc.query(
                """
                SELECT id, tipo::text AS tipo, estado::text AS estado FROM cargo
                 WHERE referencia_externa = ? AND tipo IN ('USO_AREA', 'GARANTIA')
                 ORDER BY tipo
                 FOR UPDATE
                """,
                (rs, i) -> new CargoAplicadoDeReserva(
                        rs.getObject("id", UUID.class),
                        CargoDeReserva.Tipo.valueOf(rs.getString("tipo")),
                        "ANULADO".equals(rs.getString("estado"))),
                reservaId.toString());
    }

    @Override
    public UUID crear(UUID unidadId, UUID reservaId, CargoDeReserva cargo, LocalDate hoy) {
        List<UUID> creado = jdbc.query(
                """
                INSERT INTO cargo (unidad_id, tipo, concepto, monto, saldo, fecha_emision, fecha_vencimiento,
                                   referencia_externa)
                VALUES (?, ?::tipo_cargo, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (referencia_externa, tipo) WHERE referencia_externa IS NOT NULL DO NOTHING
                RETURNING id
                """,
                (rs, i) -> rs.getObject("id", UUID.class),
                unidadId,
                cargo.tipo().name(),
                cargo.concepto(),
                cargo.monto().monto(),
                cargo.monto().monto(),
                hoy,
                cargo.fechaVencimiento(),
                reservaId.toString());
        if (!creado.isEmpty()) {
            return creado.get(0);
        }
        return jdbc.queryForObject(
                "SELECT id FROM cargo WHERE referencia_externa = ? AND tipo = ?::tipo_cargo",
                UUID.class,
                reservaId.toString(),
                cargo.tipo().name());
    }

    @Override
    public Dinero anular(UUID cargoId, String motivo, String referencia) {
        // F7: lo que se pagó de este cargo deja de estar imputado y vuelve al pago como saldo a favor. La nota de
        // crédito queda como registro de la anulación (F6); el cargo no se borra ni cambia su monto.
        BigDecimal pagado = jdbc.queryForObject(
                "SELECT COALESCE(sum(monto), 0) FROM imputacion_pago WHERE cargo_id = ?", BigDecimal.class, cargoId);
        jdbc.update("DELETE FROM imputacion_pago WHERE cargo_id = ?", cargoId);
        jdbc.update(
                """
                INSERT INTO nota_credito (cargo_id, monto, motivo, referencia_externa, creada_por)
                SELECT id, monto, left(?, 300), ?, ? FROM cargo WHERE id = ?
                ON CONFLICT (referencia_externa) DO NOTHING
                """,
                motivo,
                referencia,
                AUTOR,
                cargoId);
        jdbc.update("UPDATE cargo SET estado = 'ANULADO', saldo = 0 WHERE id = ? AND estado <> 'ANULADO'", cargoId);
        return Dinero.de(pagado == null ? BigDecimal.ZERO : pagado);
    }
}
