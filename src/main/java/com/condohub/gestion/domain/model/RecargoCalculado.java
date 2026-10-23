package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Recargo calculado para un cargo vencido, antes de proponerlo (F5). */
public record RecargoCalculado(UUID cargoOrigenId, Dinero base, Dinero monto) {}
