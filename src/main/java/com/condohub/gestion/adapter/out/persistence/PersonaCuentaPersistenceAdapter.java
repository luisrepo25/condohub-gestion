package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.PersonaCuentaRepository;
import com.condohub.gestion.domain.model.PersonaCuenta;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersonaCuentaPersistenceAdapter implements PersonaCuentaRepository {

    private final JdbcTemplate jdbc;

    PersonaCuentaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PersonaCuenta> porId(UUID personaId) {
        return jdbc.query(
                        "SELECT id, nombre, keycloak_sub FROM persona WHERE id = ?",
                        (rs, i) -> new PersonaCuenta(
                                rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("keycloak_sub")),
                        personaId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UUID> personaIdDeCuenta(String keycloakSub) {
        return jdbc.queryForList("SELECT id FROM persona WHERE keycloak_sub = ?", UUID.class, keycloakSub).stream()
                .findFirst();
    }

    @Override
    public void guardarEnlace(UUID personaId, String keycloakSub) {
        jdbc.update("UPDATE persona SET keycloak_sub = ? WHERE id = ?", keycloakSub, personaId);
    }
}