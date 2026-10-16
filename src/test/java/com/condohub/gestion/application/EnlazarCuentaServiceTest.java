package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.out.PersonaCuentaRepository;
import com.condohub.gestion.application.usecase.EnlazarCuentaService;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.PersonaCuenta;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnlazarCuentaServiceTest {

    private final Map<UUID, PersonaCuenta> personas = new HashMap<>();
    private final EnlazarCuentaService servicio = new EnlazarCuentaService(new PersonaCuentaRepository() {
        @Override
        public Optional<PersonaCuenta> porId(UUID id) {
            return Optional.ofNullable(personas.get(id));
        }

        @Override
        public Optional<UUID> personaIdDeCuenta(String sub) {
            return personas.values().stream().filter(p -> sub.equals(p.keycloakSub())).map(PersonaCuenta::personaId).findFirst();
        }

        @Override
        public void guardarEnlace(UUID id, String sub) {
            personas.put(id, personas.get(id).enlazarCon(sub));
        }
    });

    private PersonaCuenta nueva(String sub) {
        var p = new PersonaCuenta(UUID.randomUUID(), "Persona", sub);
        personas.put(p.personaId(), p);
        return p;
    }

    @Test
    void F1_enlaza_una_persona_sin_cuenta() {
        var p = nueva(null);
        assertThat(servicio.enlazar(p.personaId(), "sub-1").keycloakSub()).isEqualTo("sub-1");
        assertThat(personas.get(p.personaId()).keycloakSub()).isEqualTo("sub-1");
    }

    @Test
    void F1_repetir_el_mismo_enlace_no_falla() {
        var p = nueva("sub-1");
        assertThat(servicio.enlazar(p.personaId(), "sub-1").keycloakSub()).isEqualTo("sub-1");
    }

    @Test
    void F1_una_cuenta_no_puede_enlazarse_a_dos_personas() {
        nueva("sub-1");
        var otra = nueva(null);
        assertThatThrownBy(() -> servicio.enlazar(otra.personaId(), "sub-1"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void F1_una_persona_no_cambia_de_cuenta_sin_mas() {
        var p = nueva("sub-1");
        assertThatThrownBy(() -> servicio.enlazar(p.personaId(), "sub-2"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void persona_inexistente_da_no_encontrado() {
        assertThatThrownBy(() -> servicio.enlazar(UUID.randomUUID(), "sub-1")).isInstanceOf(RecursoNoEncontradoException.class);
    }
}