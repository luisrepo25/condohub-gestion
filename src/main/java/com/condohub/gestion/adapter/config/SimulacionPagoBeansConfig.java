package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.SimularPagoUseCase;
import com.condohub.gestion.application.port.out.CargosDeUnidadRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.application.usecase.SimularPagoService;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code simularPago} con sus puertos de salida. */
@Configuration
class SimulacionPagoBeansConfig {

    @Bean
    SimularPagoUseCase simularPago(
            CargosDeUnidadRepository cargos,
            UnidadRepository unidades,
            PoliticaVisibilidad politica,
            ImputadorPagos imputador) {
        return new SimularPagoService(cargos, unidades, politica, imputador);
    }
}
