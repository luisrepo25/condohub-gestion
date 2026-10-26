package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Parte de un pago confirmado que todavía no se imputó a ningún cargo (F7). */
public record SaldoAFavor(UUID pagoId, Dinero disponible) {}
