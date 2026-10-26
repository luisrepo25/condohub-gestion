package com.condohub.gestion.adapter.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mensajería (S4): activa el envío periódico del outbox y declara el exchange {@code condohub.events} igual que
 * {@code infra/rabbitmq/definitions.json} (topic, durable), por si el broker aún no lo tiene.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "condohub.outbox.relay.habilitado", havingValue = "true", matchIfMissing = true)
class MensajeriaConfig {

    @Bean
    TopicExchange eventosCondohub(@Value("${condohub.outbox.exchange:condohub.events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }
}
