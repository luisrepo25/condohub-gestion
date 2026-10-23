package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.EmitirExpensasUseCase;
import com.condohub.gestion.application.port.out.AlicuotaRepository;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.PeriodoExpensasRepository;
import com.condohub.gestion.application.usecase.EmitirExpensasService;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code emitirExpensas} con sus puertos de salida. */
@Configuration
class ExpensasBeansConfig {

    @Bean
    CalculadoraExpensas calculadoraExpensas() {
        return new CalculadoraExpensas();
    }

    @Bean
    EmitirExpensasUseCase emitirExpensas(
            PeriodoExpensasRepository periodos,
            AlicuotaRepository alicuotas,
            ParametroRepository parametros,
            CalculadoraExpensas calculadora) {
        return new EmitirExpensasService(periodos, alicuotas, parametros, calculadora);
    }
}
