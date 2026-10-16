package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ConsultarMiPerfilUseCase;
import com.condohub.gestion.domain.model.Rol;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolver de {@code miPerfil}. Sin lógica de negocio: arma el {@code Solicitante} y traduce el resultado. */
@Controller
class PerfilQueryController {

    private final ConsultarMiPerfilUseCase consultarMiPerfil;

    PerfilQueryController(ConsultarMiPerfilUseCase consultarMiPerfil) {
        this.consultarMiPerfil = consultarMiPerfil;
    }

    record UnidadDePersonaDto(UUID unidadId, String codigo, String bloque, boolean titular) {}

    record PerfilDto(String sub, List<String> roles, UUID personaId, String nombre, List<UnidadDePersonaDto> unidades) {}

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE','GUARDIA')")
    PerfilDto miPerfil(Authentication autenticacion) {
        var perfil = consultarMiPerfil.consultar(SolicitanteMapper.desde(autenticacion));
        var persona = perfil.persona();
        return new PerfilDto(
                perfil.sub(),
                perfil.roles().stream().map(Rol::name).sorted().toList(),
                persona == null ? null : persona.personaId(),
                persona == null ? null : persona.nombre(),
                persona == null
                        ? List.of()
                        : persona.unidades().stream()
                                .map(u -> new UnidadDePersonaDto(u.unidadId(), u.codigo(), u.bloque(), u.titular()))
                                .toList());
    }
}
