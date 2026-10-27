package com.condohub.gestion.adapter.config;

import com.condohub.gestion.adapter.in.messaging.ComandosReservaListener;
import com.condohub.gestion.application.port.in.AnularCargosReservaUseCase;
import com.condohub.gestion.application.port.in.AplicarCargosReservaUseCase;
import com.condohub.gestion.application.port.out.CargoReservaRepository;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.RespuestasSaga;
import com.condohub.gestion.application.usecase.AnularCargosReservaService;
import com.condohub.gestion.application.usecase.AplicarCargosReservaService;
import com.condohub.gestion.application.usecase.AplicarSaldoAFavor;
import com.condohub.gestion.domain.service.CargosDeReserva;
import java.time.Clock;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Saga de reserva, paso 2 (R5–R7, S5): casos de uso de cargos de reserva y su consumidor de RabbitMQ. La cola y sus
 * enlaces se declaran igual que en {@code infra/rabbitmq/definitions.json} (mismos argumentos; si ya existen, no cambia
 * nada). El consumidor se apaga con {@code condohub.saga.consumidor.habilitado=false} (pruebas sin broker).
 */
@Configuration
class SagaReservasConfig {

    @Bean
    CargosDeReserva cargosDeReserva() {
        return new CargosDeReserva();
    }

    @Bean
    AplicarCargosReservaUseCase aplicarCargosReserva(
            CargoReservaRepository cargos,
            ParametroRepository parametros,
            RespuestasSaga respuestas,
            AplicarSaldoAFavor saldoAFavor,
            CargosDeReserva cargosDeReserva,
            Clock reloj) {
        return new AplicarCargosReservaService(cargos, parametros, respuestas, saldoAFavor, cargosDeReserva, reloj);
    }

    @Bean
    AnularCargosReservaUseCase anularCargosReserva(
            CargoReservaRepository cargos, RespuestasSaga respuestas, AplicarSaldoAFavor saldoAFavor) {
        return new AnularCargosReservaService(cargos, respuestas, saldoAFavor);
    }

    @Configuration
    @ConditionalOnProperty(name = "condohub.saga.consumidor.habilitado", havingValue = "true", matchIfMissing = true)
    static class Consumidor {

        @Bean
        ComandosReservaListener comandosReservaListener(AplicarCargosReservaUseCase aplicar, AnularCargosReservaUseCase anular) {
            return new ComandosReservaListener(aplicar, anular);
        }

        @Bean
        Declarables colaFinanzasReservas(
                @Value("${condohub.outbox.exchange:condohub.events}") String exchange,
                @Value("${condohub.saga.cola:finanzas.reservas}") String nombreCola) {
            TopicExchange eventos = new TopicExchange(exchange, true, false);
            Queue cola = new Queue(nombreCola, true, false, false, Map.of(
                    "x-queue-type", "classic",
                    "x-dead-letter-exchange", "condohub.events.dlx"));
            Binding aplicar = BindingBuilder.bind(cola).to(eventos).with("reservas.AplicarCargosReserva");
            Binding anular = BindingBuilder.bind(cola).to(eventos).with("reservas.AnularCargosReserva");
            return new Declarables(eventos, cola, aplicar, anular);
        }
    }
}
