package com.condohub.gestion.domain.event;

import java.util.UUID;

/** Evento de dominio del contexto Finanzas que se publica a otros servicios con Outbox (S4). */
public sealed interface EventoFinanzas permits PagoRegistrado, PagoConfirmado {

    /** Tipo y routing key: {@code finanzas.<Evento>} (contracts/events). */
    String tipo();

    /** Id del agregado que lo produjo. */
    UUID agregadoId();

    /** Nombre del agregado que lo produjo (columna {@code aggregate_type} del outbox): {@code Pago}, {@code Cargo}... */
    String tipoAgregado();
}
