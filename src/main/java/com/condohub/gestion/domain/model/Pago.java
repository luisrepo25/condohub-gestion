package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.LocalDate;
import java.util.UUID;

/** Vista de lectura de un pago. Las transiciones (confirmar, rechazar) se agregan con sus casos de uso. */
public record Pago(UUID id, UUID unidadId, Dinero monto, LocalDate fechaPago, EstadoPago estado) {}
