package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Monto de un pago aplicado a un cargo (una fila de {@code imputacion_pago}, F7). */
public record ImputacionDePago(UUID pagoId, UUID cargoId, Dinero monto) {}
