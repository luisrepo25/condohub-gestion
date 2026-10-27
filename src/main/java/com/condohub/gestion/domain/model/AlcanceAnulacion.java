package com.condohub.gestion.domain.model;

import java.util.Set;

/**
 * Qué cargos de una reserva anula Reservas (evento {@code reservas.AnularCargosReserva}): todos (saga fallida o
 * cancelación a tiempo, R7) o solo la garantía (cancelación tardía, R7, o finalizada sin daños, R6).
 */
public enum AlcanceAnulacion {
    TODOS(Set.of(CargoDeReserva.Tipo.USO_AREA, CargoDeReserva.Tipo.GARANTIA)),
    SOLO_GARANTIA(Set.of(CargoDeReserva.Tipo.GARANTIA));

    private final Set<CargoDeReserva.Tipo> tipos;

    AlcanceAnulacion(Set<CargoDeReserva.Tipo> tipos) {
        this.tipos = tipos;
    }

    public boolean incluye(CargoDeReserva.Tipo tipo) {
        return tipos.contains(tipo);
    }
}
