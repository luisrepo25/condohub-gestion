package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.ConsultarEstadoDeCuentaUseCase;
import com.condohub.gestion.application.port.in.ListarPagosPendientesUseCase;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.application.port.out.PagoRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.application.usecase.ConsultarEstadoDeCuentaService;
import com.condohub.gestion.application.usecase.ListarPagosPendientesService;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso (application) con sus puertos de salida; la aplicación no conoce Spring. */
@Configuration
class BeansConfig {

    @Bean
    PoliticaVisibilidad politicaVisibilidad() {
        return new PoliticaVisibilidad();
    }

    @Bean
    ConsultarEstadoDeCuentaUseCase consultarEstadoDeCuenta(
            EstadoDeCuentaRepository estados, UnidadRepository unidades, PoliticaVisibilidad politica) {
        return new ConsultarEstadoDeCuentaService(estados, unidades, politica);
    }

    @Bean
    ListarPagosPendientesUseCase listarPagosPendientes(PagoRepository pagos) {
        return new ListarPagosPendientesService(pagos);
    }
}