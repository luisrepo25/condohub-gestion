package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.AlicuotaRepository;
import com.condohub.gestion.domain.valueobject.Alicuota;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlicuotaPersistenceAdapter implements AlicuotaRepository {

    private final JdbcTemplate jdbc;

    AlicuotaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Alicuota> deUnidadesActivas() {
        Map<UUID, Alicuota> alicuotas = new LinkedHashMap<>();
        jdbc.query(
                "SELECT id, alicuota FROM unidad WHERE activa ORDER BY codigo",
                rs -> {
                    alicuotas.put(
                            rs.getObject("id", UUID.class), Alicuota.de(rs.getObject("alicuota", BigDecimal.class)));
                });
        return alicuotas;
    }
}
