package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.MorosoRepository;
import com.condohub.gestion.domain.model.Moroso;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MorosoPersistenceAdapter implements MorosoRepository {

    private static final String MOROSOS = """
            SELECT v.unidad_id, v.codigo, v.bloque, v.deuda_vencida, v.expensas_vencidas,
                   p.nombre, p.email, p.telefono
              FROM v_estado_cuenta v
              JOIN unidad u  ON u.id = v.unidad_id
              JOIN persona p ON p.id = u.propietario_titular_id
             WHERE v.moroso
            """;

    private final JdbcTemplate jdbc;

    MorosoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Moroso> morosos() {
        return jdbc.query(
                MOROSOS,
                (rs, i) -> new Moroso(
                        rs.getObject("unidad_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("bloque"),
                        Dinero.de(rs.getBigDecimal("deuda_vencida")),
                        rs.getLong("expensas_vencidas"),
                        rs.getString("nombre"),
                        rs.getString("email"),
                        rs.getString("telefono")));
    }
}