package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ConsultarMiPerfilUseCase;
import com.condohub.gestion.application.port.out.PersonaRepository;
import com.condohub.gestion.domain.model.PerfilDeUsuario;
import com.condohub.gestion.domain.model.Solicitante;
import org.springframework.transaction.annotation.Transactional;

public class ConsultarMiPerfilService implements ConsultarMiPerfilUseCase {

    private final PersonaRepository personas;

    public ConsultarMiPerfilService(PersonaRepository personas) {
        this.personas = personas;
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilDeUsuario consultar(Solicitante solicitante) {
        return new PerfilDeUsuario(
                solicitante.sub(), solicitante.roles(), personas.porKeycloakSub(solicitante.sub()).orElse(null));
    }
}