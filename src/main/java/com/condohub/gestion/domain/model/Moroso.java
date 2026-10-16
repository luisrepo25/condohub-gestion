package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/**
 * Unidad morosa (con al menos un cargo vencido e impago) y cómo contactar a su propietario titular (F1), para que
 * n8n y el agente envíen recordatorios (G1).
 */
public record Moroso(
        UUID unidadId,
        String codigo,
        String bloque,
        Dinero deudaVencida,
        long expensasVencidas,
        String propietarioNombre,
        String propietarioEmail,
        String propietarioTelefono) {}