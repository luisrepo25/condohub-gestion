package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.PersonaRepository;
import com.condohub.gestion.domain.model.PerfilDeUsuario.PersonaEnlazada;
import com.condohub.gestion.domain.model.PerfilDeUsuario.UnidadDePersona;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PersonaPersistenceAdapter implements PersonaRepository {

    private static final String UNIDADES = """
            SELECT u.id, u.codigo, u.bloque, true AS titular FROM unidad u
             WHERE u.propietario_titular_id = ?
            UNION
            SELECT u.id, u.codigo, u.bloque, false FROM residente_unidad ru
              JOIN unidad u ON u.id = ru.unidad_id
             WHERE ru.persona_id = ?
               AND ru.desde <= current_date
               AND (ru.hasta IS NULL OR ru.hasta >= current_date)
             ORDER BY 2
            """;

    private final JdbcTemplate jdbc;

    PersonaPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PersonaEnlazada> porKeycloakSub(String keycloakSub) {
        return jdbc
                .query("SELECT id, nombre FROM persona WHERE keycloak_sub = ?",
                        (rs, i) -> new Object[] {rs.getObject("id", UUID.class), rs.getString("nombre")},
                        keycloakSub)
                .stream()
                .findFirst()
                .map(fila -> new PersonaEnlazada((UUID) fila[0], (String) fila[1], unidadesDe((UUID) fila[0])));
    }

    private List<UnidadDePersona> unidadesDe(UUID personaId) {
        return jdbc.query(
                UNIDADES,
                (rs, i) -> new UnidadDePersona(
                        rs.getObject("id", UUID.class), rs.getString("codigo"), rs.getString("bloque"), rs.getBoolean("titular")),
                personaId,
                personaId);
    }
}
