package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.application.usecase.ConsultarMiPerfilService;
import com.condohub.gestion.domain.model.PerfilDeUsuario.PersonaEnlazada;
import com.condohub.gestion.domain.model.PerfilDeUsuario.UnidadDePersona;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarMiPerfilServiceTest {

    private final UUID unidadId = UUID.randomUUID();

    @Test
    void F1_residente_enlazado_recibe_su_persona_y_sus_unidades() {
        var persona = new PersonaEnlazada(
                UUID.randomUUID(), "Residente Demo", List.of(new UnidadDePersona(unidadId, "A-101", "A", true)));
        var servicio = new ConsultarMiPerfilService(sub -> sub.equals("sub-res") ? Optional.of(persona) : Optional.empty());

        var perfil = servicio.consultar(new Solicitante("sub-res", Set.of(Rol.RESIDENTE)));

        assertThat(perfil.persona()).isEqualTo(persona);
        assertThat(perfil.roles()).containsExactly(Rol.RESIDENTE);
    }

    @Test
    void F1_cuenta_sin_persona_enlazada_recibe_perfil_sin_unidades() {
        var servicio = new ConsultarMiPerfilService(sub -> Optional.empty());

        var perfil = servicio.consultar(new Solicitante("sub-admin", Set.of(Rol.ADMIN)));

        assertThat(perfil.persona()).isNull();
        assertThat(perfil.sub()).isEqualTo("sub-admin");
    }
}
