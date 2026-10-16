package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.EnlazarCuentaUseCase;
import com.condohub.gestion.application.port.out.PersonaCuentaRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.PersonaCuenta;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class EnlazarCuentaService implements EnlazarCuentaUseCase {

    private final PersonaCuentaRepository personas;

    public EnlazarCuentaService(PersonaCuentaRepository personas) {
        this.personas = personas;
    }

    @Override
    @Transactional
    public PersonaCuenta enlazar(UUID personaId, String keycloakSub) {
        var persona = personas.porId(personaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la persona " + personaId));
        var duenia = personas.personaIdDeCuenta(keycloakSub);
        if (duenia.isPresent() && !duenia.get().equals(personaId)) {
            throw new ReglaNegocioVioladaException("F1", "La cuenta ya está enlazada a otra persona");
        }
        var enlazada = persona.enlazarCon(keycloakSub);
        if (!enlazada.equals(persona)) {
            personas.guardarEnlace(personaId, keycloakSub);
        }
        return enlazada;
    }
}