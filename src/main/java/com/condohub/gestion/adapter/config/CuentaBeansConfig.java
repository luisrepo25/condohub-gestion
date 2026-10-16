package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.EnlazarCuentaUseCase;
import com.condohub.gestion.application.port.out.PersonaCuentaRepository;
import com.condohub.gestion.application.usecase.EnlazarCuentaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code enlazarCuenta} con su puerto de salida. */
@Configuration
class CuentaBeansConfig {

    @Bean
    EnlazarCuentaUseCase enlazarCuenta(PersonaCuentaRepository personas) {
        return new EnlazarCuentaService(personas);
    }
}