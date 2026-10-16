package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.EnlazarCuentaUseCase;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/** Resolver de {@code enlazarCuenta}. Sin lógica de negocio: delega en el caso de uso. */
@Controller
class CuentaMutationController {

    private final EnlazarCuentaUseCase enlazarCuenta;

    CuentaMutationController(EnlazarCuentaUseCase enlazarCuenta) {
        this.enlazarCuenta = enlazarCuenta;
    }

    record PersonaCuentaDto(UUID personaId, String nombre, String keycloakSub) {}

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    PersonaCuentaDto enlazarCuenta(@Argument UUID personaId, @Argument String keycloakSub) {
        var p = enlazarCuenta.enlazar(personaId, keycloakSub);
        return new PersonaCuentaDto(p.personaId(), p.nombre(), p.keycloakSub());
    }
}