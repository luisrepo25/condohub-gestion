package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.model.SaldoAFavor;
import com.condohub.gestion.domain.service.AplicadorSaldoAFavor;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AplicadorSaldoAFavorTest {

    private final AplicadorSaldoAFavor aplicador = new AplicadorSaldoAFavor(new ImputadorPagos());

    private final CargoPendiente noviembre = cargo("390.00", "2026-11-10", "2026-11-01T00:00:00Z");
    private final CargoPendiente diciembre = cargo("390.00", "2026-12-10", "2026-12-01T00:00:00Z");

    @Test
    void F7_el_saldo_a_favor_paga_parte_de_la_expensa_nueva() {
        var pago = UUID.randomUUID();

        var imputaciones = aplicador.aplicar(List.of(new SaldoAFavor(pago, Dinero.de("110"))), List.of(noviembre));

        assertThat(imputaciones).containsExactly(new ImputacionDePago(pago, noviembre.id(), Dinero.de("110")));
    }

    @Test
    void F7_se_consume_primero_el_saldo_del_pago_mas_antiguo() {
        var antiguo = UUID.randomUUID();
        var reciente = UUID.randomUUID();

        var imputaciones = aplicador.aplicar(
                List.of(new SaldoAFavor(antiguo, Dinero.de("300")), new SaldoAFavor(reciente, Dinero.de("200"))),
                List.of(noviembre, diciembre));

        // 300 del antiguo a noviembre; 90 del reciente completan noviembre y 110 van a diciembre.
        assertThat(imputaciones).containsExactly(
                new ImputacionDePago(antiguo, noviembre.id(), Dinero.de("300")),
                new ImputacionDePago(reciente, noviembre.id(), Dinero.de("90")),
                new ImputacionDePago(reciente, diciembre.id(), Dinero.de("110")));
    }

    @Test
    void F7_si_el_saldo_supera_la_deuda_lo_que_sobra_sigue_a_favor() {
        var pago = UUID.randomUUID();

        var imputaciones = aplicador.aplicar(List.of(new SaldoAFavor(pago, Dinero.de("1000"))), List.of(noviembre));

        assertThat(imputaciones).containsExactly(new ImputacionDePago(pago, noviembre.id(), Dinero.de("390")));
    }

    @Test
    void F7_sin_cargos_pendientes_no_se_aplica_nada() {
        assertThat(aplicador.aplicar(List.of(new SaldoAFavor(UUID.randomUUID(), Dinero.de("50"))), List.of()))
                .isEmpty();
    }

    private static CargoPendiente cargo(String saldo, String vence, String creado) {
        return new CargoPendiente(UUID.randomUUID(), Dinero.de(saldo), LocalDate.parse(vence), Instant.parse(creado));
    }
}
