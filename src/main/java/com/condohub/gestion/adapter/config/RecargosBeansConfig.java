package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.ProponerRecargoUseCase;
import com.condohub.gestion.application.port.in.ResolverRecargoUseCase;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.RecargoRepository;
import com.condohub.gestion.application.usecase.AplicarSaldoAFavor;
import com.condohub.gestion.application.usecase.ProponerRecargoService;
import com.condohub.gestion.application.usecase.ResolverRecargoService;
import com.condohub.gestion.domain.service.CalculadoraRecargos;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso de recargos por mora (F5) con sus puertos de salida. */
@Configuration
class RecargosBeansConfig {

    @Bean
    CalculadoraRecargos calculadoraRecargos() {
        return new CalculadoraRecargos();
    }

    @Bean
    ProponerRecargoUseCase proponerRecargo(
            RecargoRepository recargos, ParametroRepository parametros, CalculadoraRecargos calculadora, Clock reloj) {
        return new ProponerRecargoService(recargos, parametros, calculadora, reloj);
    }

    @Bean
    ResolverRecargoUseCase resolverRecargo(
            RecargoRepository recargos, ParametroRepository parametros, AplicarSaldoAFavor saldoAFavor, Clock reloj) {
        return new ResolverRecargoService(recargos, parametros, saldoAFavor, reloj);
    }
}
