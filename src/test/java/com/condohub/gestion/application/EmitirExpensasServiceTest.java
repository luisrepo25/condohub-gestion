package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.out.PeriodoExpensasRepository;
import com.condohub.gestion.application.usecase.EmitirExpensasService;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.PeriodoExpensas;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import com.condohub.gestion.domain.valueobject.Alicuota;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmitirExpensasServiceTest {

    private final PeriodosEnMemoria periodos = new PeriodosEnMemoria();
    private final Map<UUID, Alicuota> alicuotas = new LinkedHashMap<>();
    /** "Hoy" es el 22/10/2026. */
    private final Clock hoy = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);

    private final EmitirExpensasService servicio =
            new EmitirExpensasService(periodos, () -> alicuotas, clave -> 10, new CalculadoraExpensas(), hoy);

    @Test
    void F3_no_se_emiten_expensas_de_un_mes_pasado() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("100"));

        assertThatThrownBy(() -> servicio.emitir("sub-admin", Periodo.de("2026-09"), Dinero.de("500")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F3"));
        assertThat(periodos.guardados).isEmpty();
    }

    @Test
    void F3_se_puede_emitir_el_mes_actual() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("100"));

        assertThat(servicio.emitir("sub-admin", Periodo.de("2026-10"), Dinero.de("500")).periodo())
                .isEqualTo(Periodo.de("2026-10"));
    }

    @Test
    void F3_emite_una_expensa_por_unidad_activa_y_la_guarda() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("60"));
        alicuotas.put(UUID.randomUUID(), Alicuota.de("40"));

        var emitido = servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("1000"));

        assertThat(emitido.expensas()).hasSize(2);
        assertThat(emitido.total()).isEqualTo(Dinero.de("1000"));
        assertThat(emitido.emitidoPor()).isEqualTo("sub-admin");
        assertThat(periodos.guardados).containsExactly(emitido);
    }

    @Test
    void F4_las_expensas_vencen_el_dia_del_parametro() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("100"));

        var emitido = servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("500"));

        assertThat(emitido.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void F3_un_periodo_ya_emitido_no_se_vuelve_a_emitir() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("100"));
        servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("500"));

        assertThatThrownBy(() -> servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("500")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F3"));
        assertThat(periodos.guardados).hasSize(1);
    }

    @Test
    void F2_si_las_alicuotas_no_suman_100_no_se_emite_nada() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("60"));
        alicuotas.put(UUID.randomUUID(), Alicuota.de("39"));

        assertThatThrownBy(() -> servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("1000")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F2"));
        assertThat(periodos.guardados).isEmpty();
    }

    @Test
    void F3_una_expensa_que_quedaria_en_cero_impide_emitir() {
        alicuotas.put(UUID.randomUUID(), Alicuota.de("99.9"));
        alicuotas.put(UUID.randomUUID(), Alicuota.de("0.1"));

        // 1.00 × 0.1 % = 0.001 → 0.00
        assertThatThrownBy(() -> servicio.emitir("sub-admin", Periodo.de("2026-11"), Dinero.de("1")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F3"));
    }

    private static final class PeriodosEnMemoria implements PeriodoExpensasRepository {

        final List<PeriodoExpensas> guardados = new ArrayList<>();

        @Override
        public boolean existe(Periodo periodo) {
            return guardados.stream().anyMatch(p -> p.periodo().equals(periodo));
        }

        @Override
        public void guardar(PeriodoExpensas periodoExpensas) {
            guardados.add(periodoExpensas);
        }
    }
}
