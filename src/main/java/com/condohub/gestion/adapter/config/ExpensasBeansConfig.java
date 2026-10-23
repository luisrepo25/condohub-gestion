package com.condohub.gestion.adapter.config;

import com.condohub.gestion.application.port.in.EmitirExpensasUseCase;
import com.condohub.gestion.application.port.out.AlicuotaRepository;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.PeriodoExpensasRepository;
import com.condohub.gestion.application.usecase.EmitirExpensasService;
import com.condohub.gestion.application.port.out.SaldoAFavorRepository;
import com.condohub.gestion.application.usecase.AplicarSaldoAFavor;
import com.condohub.gestion.domain.service.AplicadorSaldoAFavor;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import com.condohub.gestion.domain.service.ImputadorPagos;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de {@code emitirExpensas} con sus puertos de salida. */
@Configuration
class ExpensasBeansConfig {

    @Bean
    CalculadoraExpensas calculadoraExpensas() {
        return new CalculadoraExpensas();
    }

    /** Reloj del sistema en UTC (fechas en UTC); en los tests se reemplaza por uno fijo. */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /** F7: aplica el saldo a favor a los cargos nuevos; lo usan los casos de uso que crean cargos. */
    @Bean
    AplicarSaldoAFavor aplicarSaldoAFavor(SaldoAFavorRepository saldos, ImputadorPagos imputador) {
        return new AplicarSaldoAFavor(saldos, new AplicadorSaldoAFavor(imputador));
    }

    @Bean
    EmitirExpensasUseCase emitirExpensas(
            PeriodoExpensasRepository periodos,
            AlicuotaRepository alicuotas,
            ParametroRepository parametros,
            CalculadoraExpensas calculadora,
            Clock reloj,
            AplicarSaldoAFavor saldoAFavor) {
        return new EmitirExpensasService(periodos, alicuotas, parametros, calculadora, reloj, saldoAFavor);
    }
}
