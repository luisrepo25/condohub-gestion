package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.RegistroPagoRepository;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.RegistroDePago;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RegistroPagoPersistenceAdapter implements RegistroPagoRepository {

    private static final String COLUMNAS = "id, unidad_id, monto, fecha_pago, estado";

    private final JdbcTemplate jdbc;

    RegistroPagoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Pago> porIdempotencyKey(IdempotencyKey clave) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pago WHERE idempotency_key = ?", (rs, i) -> pago(rs), clave.valor())
                .stream()
                .findFirst();
    }

    @Override
    public boolean existeUnidad(UUID unidadId) {
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM unidad WHERE id = ?", Integer.class, unidadId);
        return cantidad != null && cantidad > 0;
    }

    @Override
    public Pago guardar(RegistroDePago r) {
        // ON CONFLICT: si una petición con la misma clave se adelantó, no se duplica ni se aborta la transacción (O3).
        var insertado = jdbc.query(
                """
                INSERT INTO pago (unidad_id, monto, fecha_pago, banco, idempotency_key, registrado_por, hora_dispositivo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING """ + " " + COLUMNAS,
                (rs, i) -> pago(rs),
                r.unidadId(),
                r.monto().monto(),
                r.fechaPago(),
                r.banco(),
                r.idempotencyKey().valor(),
                r.registradoPor(),
                r.horaDispositivo() == null ? null : Timestamp.from(r.horaDispositivo()));
        return insertado.stream().findFirst().or(() -> porIdempotencyKey(r.idempotencyKey())).orElseThrow();
    }

    private static Pago pago(ResultSet rs) throws SQLException {
        return new Pago(
                rs.getObject("id", UUID.class),
                rs.getObject("unidad_id", UUID.class),
                Dinero.de(rs.getObject("monto", BigDecimal.class)),
                rs.getObject("fecha_pago", LocalDate.class),
                EstadoPago.valueOf(rs.getString("estado")));
    }
}
