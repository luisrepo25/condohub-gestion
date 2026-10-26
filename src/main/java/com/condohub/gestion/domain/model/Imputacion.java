package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Parte de un pago confirmado aplicada a un cargo (F7). */
public record Imputacion(UUID cargoId, Dinero monto) {}
