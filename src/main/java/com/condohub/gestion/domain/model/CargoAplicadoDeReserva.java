package com.condohub.gestion.domain.model;

import java.util.UUID;

/** Un cargo ya creado por una reserva (`referencia_externa` = reservaId) y si sigue vigente o fue anulado (F6). */
public record CargoAplicadoDeReserva(UUID id, CargoDeReserva.Tipo tipo, boolean anulado) {}
