package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.ConsultarMorososUseCase;
import com.condohub.gestion.application.port.out.MorosoRepository;
import com.condohub.gestion.application.usecase.ConsultarMorososService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code morosos} con su puerto de salida. */
@Configuration
class MorososBeansConfig {

    @Bean
    ConsultarMorososUseCase consultarMorosos(MorosoRepository morosos) {
        return new ConsultarMorososService(morosos);
    }
}