package com.condohub.gestion.domain.model;

/** Ciclo de un recargo por mora (F5): solo se cobra cuando el ADMIN lo confirma. */
public enum EstadoRecargo {
    PROPUESTO,
    CONFIRMADO,
    RECHAZADO
}
