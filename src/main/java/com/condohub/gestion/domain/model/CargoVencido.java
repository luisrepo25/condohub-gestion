package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** Cargo vencido e impago sobre el que se puede proponer un recargo (F4, F5). */
public record CargoVencido(UUID id, Dinero saldo) {}
