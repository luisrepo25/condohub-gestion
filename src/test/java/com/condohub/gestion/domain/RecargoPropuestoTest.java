package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.EstadoRecargo;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecargoPropuestoTest {

    private static final LocalDate HOY = LocalDate.parse("2026-10-25");

    /** Propuesto sobre un cargo que debía 300.00: 2 % = 6.00. */
    private final RecargoPropuesto propuesta = new RecargoPropuesto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Periodo.de("2026-10"),
            new BigDecimal("2.00"),
            Dinero.de("300.00"),
            Dinero.de("6.00"),
            EstadoRecargo.PROPUESTO,
            "ADMIN",
            null,
            null);

    @Test
    void F5_se_confirma_si_el_cargo_de_origen_sigue_debiendo_lo_mismo() {
        assertThat(propuesta.confirmar(HOY, 10, Dinero.de("300"))).isEqualTo(LocalDate.parse("2026-11-10"));
    }

    @Test
    void F5_no_se_confirma_un_recargo_sobre_un_cargo_ya_pagado() {
        assertThatThrownBy(() -> propuesta.confirmar(HOY, 10, Dinero.CERO))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo("F5");
                    assertThat(e.getMessage()).contains("ya fue pagado");
                });
    }

    @Test
    void F5_no_se_confirma_si_el_cargo_de_origen_se_pago_en_parte() {
        assertThatThrownBy(() -> propuesta.confirmar(HOY, 10, Dinero.de("100.00")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo("F5");
                    assertThat(e.getMessage()).contains("era 300.00, hoy 100.00");
                });
    }

    @Test
    void F5_un_recargo_ya_resuelto_avisa_eso_antes_que_el_saldo() {
        var rechazado = new RecargoPropuesto(
                propuesta.id(), propuesta.unidadId(), propuesta.cargoOrigenId(), propuesta.periodo(),
                propuesta.porcentaje(), propuesta.base(), propuesta.monto(), EstadoRecargo.RECHAZADO, "ADMIN", null, null);

        assertThatThrownBy(() -> rechazado.confirmar(HOY, 10, Dinero.CERO))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class,
                        e -> assertThat(e.getMessage()).contains("ya fue resuelto"));
    }
}
