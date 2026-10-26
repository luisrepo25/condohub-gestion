package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.Imputacion;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImputadorPagosTest {

    private final ImputadorPagos imputador = new ImputadorPagos();

    private final CargoPendiente agosto = cargo("300.00", "2026-08-10", "2026-08-01T12:00:00Z");
    private final CargoPendiente septiembre = cargo("300.00", "2026-09-10", "2026-09-01T12:00:00Z");
    private final CargoPendiente noviembre = cargo("300.00", "2026-11-10", "2026-11-01T12:00:00Z");

    @Test
    void F7_pago_se_imputa_al_cargo_vencido_mas_antiguo() {
        // Llegan desordenados: igual se paga primero agosto.
        var resultado = imputador.imputar(Dinero.de("300"), List.of(noviembre, septiembre, agosto));

        assertThat(resultado.imputaciones()).containsExactly(new Imputacion(agosto.id(), Dinero.de("300")));
        assertThat(resultado.excedente()).isEqualTo(Dinero.CERO);
    }

    @Test
    void F7_un_pago_parcial_deja_saldo_en_el_siguiente_cargo() {
        var resultado = imputador.imputar(Dinero.de("450"), List.of(agosto, septiembre, noviembre));

        assertThat(resultado.imputaciones()).containsExactly(
                new Imputacion(agosto.id(), Dinero.de("300")), new Imputacion(septiembre.id(), Dinero.de("150")));
        assertThat(resultado.imputado()).isEqualTo(Dinero.de("450"));
    }

    @Test
    void F7_si_alcanza_sigue_con_los_cargos_aun_no_vencidos() {
        var resultado = imputador.imputar(Dinero.de("900"), List.of(agosto, septiembre, noviembre));

        assertThat(resultado.imputaciones()).extracting(Imputacion::cargoId)
                .containsExactly(agosto.id(), septiembre.id(), noviembre.id());
    }

    @Test
    void F7_el_excedente_queda_como_saldo_a_favor() {
        var resultado = imputador.imputar(Dinero.de("1000"), List.of(agosto, septiembre, noviembre));

        assertThat(resultado.imputado()).isEqualTo(Dinero.de("900"));
        assertThat(resultado.excedente()).isEqualTo(Dinero.de("100"));
    }

    @Test
    void F7_sin_cargos_pendientes_todo_el_pago_es_saldo_a_favor() {
        var resultado = imputador.imputar(Dinero.de("250"), List.of());

        assertThat(resultado.imputaciones()).isEmpty();
        assertThat(resultado.excedente()).isEqualTo(Dinero.de("250"));
    }

    @Test
    void F7_a_igual_vencimiento_se_paga_primero_el_cargo_creado_antes() {
        var expensa = cargo("100.00", "2026-09-10", "2026-09-01T12:00:00Z");
        var usoDeArea = cargo("100.00", "2026-09-10", "2026-09-05T12:00:00Z");

        var resultado = imputador.imputar(Dinero.de("100"), List.of(usoDeArea, expensa));

        assertThat(resultado.imputaciones()).extracting(Imputacion::cargoId).containsExactly(expensa.id());
    }

    private static CargoPendiente cargo(String saldo, String vence, String creado) {
        return new CargoPendiente(UUID.randomUUID(), Dinero.de(saldo), LocalDate.parse(vence), Instant.parse(creado));
    }
}
