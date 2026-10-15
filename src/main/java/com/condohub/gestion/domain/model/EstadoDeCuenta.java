package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Situación financiera de una unidad (vista de lectura de {@code CuentaUnidad}). */
public record EstadoDeCuenta(
        UUID unidadId,
        String codigo,
        String bloque,
        Dinero deudaTotal,
        Dinero deudaVencida,
        long expensasVencidas,
        Dinero saldoAFavor,
        boolean moroso) {}
