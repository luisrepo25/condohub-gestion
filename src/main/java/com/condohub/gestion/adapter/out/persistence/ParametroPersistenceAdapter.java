package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.ParametroRepository;
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
        return jdbc.queryForList("SELECT valor FROM parametro WHERE clave = ?", String.class, clave).stream()
                .findFirst()
                .map(String::trim)
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave + " en la tabla parametro"));
    }
}
