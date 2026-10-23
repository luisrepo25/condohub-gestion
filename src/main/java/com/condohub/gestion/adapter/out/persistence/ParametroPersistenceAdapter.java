package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.ParametroRepository;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ParametroPersistenceAdapter implements ParametroRepository {

    private final JdbcTemplate jdbc;

    ParametroPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int entero(String clave) {
        return Integer.parseInt(valor(clave));
    }

    @Override
    public BigDecimal decimal(String clave) {
        return new BigDecimal(valor(clave));
    }

    private String valor(String clave) {
        return jdbc.queryForList("SELECT valor FROM parametro WHERE clave = ?", String.class, clave).stream()
                .findFirst()
                .map(String::trim)
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave + " en la tabla parametro"));
    }
}
