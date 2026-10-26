package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.application.port.out.SaldoAFavorRepository;
import com.condohub.gestion.application.usecase.AplicarSaldoAFavor;
import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.model.SaldoAFavor;
import com.condohub.gestion.domain.service.AplicadorSaldoAFavor;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AplicarSaldoAFavorTest {

    private final UUID unidad = UUID.randomUUID();
    private final UUID otra = UUID.randomUUID();
    private final Saldos saldos = new Saldos();
    private final AplicarSaldoAFavor caso = new AplicarSaldoAFavor(saldos, new AplicadorSaldoAFavor(new ImputadorPagos()));

    @Test
    void F7_aplicar_a_una_unidad_solo_usa_el_saldo_de_esa_unidad() {
        var aplicado = caso.aplicar(unidad);

        assertThat(aplicado).isEqualTo(Dinero.de("50"));
        assertThat(saldos.unidadesLeidas).containsExactly(unidad);
        assertThat(saldos.imputadas).allSatisfy(i -> assertThat(i.pagoId()).isEqualTo(saldos.pagoDeLaUnidad));
    }

    @Test
    void F7_una_unidad_sin_saldo_a_favor_no_imputa_nada() {
        assertThat(caso.aplicar(UUID.randomUUID())).isEqualTo(Dinero.CERO);
        assertThat(saldos.imputadas).isEmpty();
    }

    @Test
    void F7_aplicar_a_todas_sigue_recorriendo_todas_las_unidades() {
        assertThat(caso.aplicar()).isEqualTo(Dinero.de("80"));
        assertThat(saldos.unidadesLeidas).containsExactlyInAnyOrder(unidad, otra);
    }

    /** Cada unidad tiene un pago con saldo a favor y un cargo pendiente de 100. */
    private final class Saldos implements SaldoAFavorRepository {

        final UUID pagoDeLaUnidad = UUID.randomUUID();
        final UUID pagoDeLaOtra = UUID.randomUUID();
        final List<UUID> unidadesLeidas = new ArrayList<>();
        final List<ImputacionDePago> imputadas = new ArrayList<>();

        @Override
        public Map<UUID, List<SaldoAFavor>> saldosAFavorPorUnidad() {
            return Map.of(
                    unidad, List.of(new SaldoAFavor(pagoDeLaUnidad, Dinero.de("50"))),
                    otra, List.of(new SaldoAFavor(pagoDeLaOtra, Dinero.de("30"))));
        }

        @Override
        public List<CargoPendiente> cargosPendientes(UUID unidadId) {
            unidadesLeidas.add(unidadId);
            return List.of(new CargoPendiente(
                    UUID.randomUUID(), Dinero.de("100"), LocalDate.parse("2026-11-10"), Instant.parse("2026-10-25T00:00:00Z")));
        }

        @Override
        public void imputar(List<ImputacionDePago> imputaciones) {
            imputadas.addAll(imputaciones);
        }
    }
}
