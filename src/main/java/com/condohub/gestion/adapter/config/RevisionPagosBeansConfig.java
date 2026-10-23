package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.ConfirmarPagoUseCase;
import com.condohub.gestion.application.port.in.RechazarPagoUseCase;
import com.condohub.gestion.application.port.out.RevisionPagoRepository;
import com.condohub.gestion.application.usecase.ConfirmarPagoService;
import com.condohub.gestion.application.usecase.RechazarPagoService;
import com.condohub.gestion.domain.service.ImputadorPagos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso de la conciliación de pagos ({@code confirmarPago}, {@code rechazarPago}). */
@Configuration
class RevisionPagosBeansConfig {

    @Bean
    ImputadorPagos imputadorPagos() {
        return new ImputadorPagos();
    }

    @Bean
    ConfirmarPagoUseCase confirmarPago(RevisionPagoRepository pagos, ImputadorPagos imputador) {
        return new ConfirmarPagoService(pagos, imputador);
    }

    @Bean
    RechazarPagoUseCase rechazarPago(RevisionPagoRepository pagos) {
        return new RechazarPagoService(pagos);
    }
}
