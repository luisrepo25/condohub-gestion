package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.event.EventoFinanzas;

/**
 * S4: registra un evento para publicarlo. Se guarda en la misma transacción que el cambio que lo produce (Outbox);
 * otro proceso lo envía después a RabbitMQ. Publicar dos veces el mismo evento no lo duplica.
 */
public interface EventPublisher {

    void publicar(EventoFinanzas evento);
}
