package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import com.condohub.gestion.domain.valueobject.Alicuota;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculadoraExpensasTest {

    private final CalculadoraExpensas calculadora = new CalculadoraExpensas();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void F3_expensa_es_presupuesto_por_alicuota() {
        var expensas = calculadora.calcular(Dinero.de("1000"), alicuotas("50", "30", "20"));

        assertThat(expensas).containsEntry(a, Dinero.de("500.00"))
                .containsEntry(b, Dinero.de("300.00"))
                .containsEntry(c, Dinero.de("200.00"));
    }

    @Test
    void F3_redondeo_a_2_decimales_y_la_diferencia_va_a_la_unidad_de_mayor_alicuota() {
        // 100 × 33.3333 % = 33.33 (x2) y 100 × 33.3334 % = 33.33: suman 99.99, falta 0.01
        var expensas = calculadora.calcular(Dinero.de("100"), alicuotas("33.3333", "33.3333", "33.3334"));

        assertThat(expensas).containsEntry(a, Dinero.de("33.33"))
                .containsEntry(b, Dinero.de("33.33"))
                .containsEntry(c, Dinero.de("33.34"));
    }

    @Test
    void F3_si_el_redondeo_se_pasa_la_unidad_de_mayor_alicuota_paga_menos() {
        // 200 × 33.3333 % = 66.67 (x2) y 200 × 33.3334 % = 66.67: suman 200.01, sobra 0.01
        var expensas = calculadora.calcular(Dinero.de("200"), alicuotas("33.3333", "33.3333", "33.3334"));

        assertThat(expensas).containsEntry(c, Dinero.de("66.66"));
    }

    @Test
    void F3_la_suma_de_las_expensas_es_exactamente_el_presupuesto() {
        var alicuotas = new LinkedHashMap<UUID, Alicuota>();
        for (int i = 0; i < 200; i++) {
            alicuotas.put(UUID.randomUUID(), Alicuota.de("0.5"));
        }

        var expensas = calculadora.calcular(Dinero.de("12345.67"), alicuotas);

        BigDecimal suma = expensas.values().stream().map(Dinero::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("12345.67");
        assertThat(expensas).hasSize(200);
    }

    @Test
    void F2_no_se_calculan_expensas_si_las_alicuotas_no_suman_100() {
        assertThatThrownBy(() -> calculadora.calcular(Dinero.de("1000"), alicuotas("50", "30", "19")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F2"));
    }

    @Test
    void F3_el_presupuesto_debe_ser_mayor_a_cero() {
        assertThatThrownBy(() -> calculadora.calcular(Dinero.CERO, alicuotas("50", "30", "20")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F3"));
    }

    private Map<UUID, Alicuota> alicuotas(String deA, String deB, String deC) {
        var alicuotas = new LinkedHashMap<UUID, Alicuota>();
        alicuotas.put(a, Alicuota.de(deA));
        alicuotas.put(b, Alicuota.de(deB));
        alicuotas.put(c, Alicuota.de(deC));
        return alicuotas;
    }
}
