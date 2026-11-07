package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.CargosDeUnidadRepository;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Lectura de solo consulta de los cargos pendientes de una unidad (no bloquea filas, a diferencia de la confirmación). */
@Component
public class CargosDeUnidadPersistenceAdapter implements CargosDeUnidadRepository {

    private final JdbcTemplate jdbc;

    CargosDeUnidadPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CargoDeUnidad> pendientes(UUID unidadId) {
        return jdbc.query(
                """
                SELECT id, tipo, concepto, saldo, fecha_vencimiento, creado_en FROM cargo
                 WHERE unidad_id = ? AND estado = 'PENDIENTE'
                 ORDER BY fecha_vencimiento, creado_en
                """,
                (rs, i) -> new CargoDeUnidad(
                        rs.getObject("id", UUID.class),
                        rs.getString("tipo"),
                        rs.getString("concepto"),
                        Dinero.de(rs.getObject("saldo", BigDecimal.class)),
                        rs.getObject("fecha_vencimiento", LocalDate.class),
                        rs.getObject("creado_en", Timestamp.class).toInstant()),
                unidadId);
    }

    @Override
    public boolean existeUnidad(UUID unidadId) {
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM unidad WHERE id = ?", Integer.class, unidadId);
        return cantidad != null && cantidad > 0;
    }
}
