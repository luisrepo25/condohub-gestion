package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.ConsultarMiPerfilUseCase;
import com.condohub.gestion.application.port.out.PersonaRepository;
import com.condohub.gestion.application.usecase.ConsultarMiPerfilService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code miPerfil} con su puerto de salida. */
@Configuration
class PerfilBeansConfig {

    @Bean
    ConsultarMiPerfilUseCase consultarMiPerfil(PersonaRepository personas) {
        return new ConsultarMiPerfilService(personas);
    }
}
