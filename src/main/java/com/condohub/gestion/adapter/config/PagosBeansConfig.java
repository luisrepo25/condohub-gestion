package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import com.condohub.gestion.application.port.out.RegistroPagoRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.application.usecase.RegistrarPagoService;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code registrarPago} con sus puertos de salida. */
@Configuration
class PagosBeansConfig {

    @Bean
    RegistrarPagoUseCase registrarPago(
            RegistroPagoRepository pagos, UnidadRepository unidades, PoliticaVisibilidad politica) {
        return new RegistrarPagoService(pagos, unidades, politica);
    }
}
