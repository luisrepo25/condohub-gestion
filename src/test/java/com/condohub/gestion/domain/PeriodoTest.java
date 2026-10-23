package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    @Test
    void F3_periodo_es_un_anio_mes_y_empieza_el_dia_1() {
        var noviembre = Periodo.de("2026-11");

        assertThat(noviembre.toString()).isEqualTo("2026-11");
        assertThat(noviembre.primerDia()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void F3_periodo_con_formato_invalido_se_rechaza() {
        assertThatThrownBy(() -> Periodo.de("11-2026"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F3"));
        assertThatThrownBy(() -> Periodo.de("2026-13")).isInstanceOf(ReglaNegocioVioladaException.class);
    }

    @Test
    void F4_vencimiento_es_el_dia_configurado_del_mismo_mes() {
        assertThat(Periodo.de("2026-11").fechaVencimiento(10)).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void F4_dia_de_vencimiento_fuera_del_mes_se_rechaza() {
        assertThatThrownBy(() -> Periodo.de("2026-02").fechaVencimiento(30))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F4"));
        assertThatThrownBy(() -> Periodo.de("2026-11").fechaVencimiento(1)).isInstanceOf(ReglaNegocioVioladaException.class);
    }

    @Test
    void F3_periodo_se_compara_por_valor() {
        assertThat(Periodo.de("2026-11")).isEqualTo(Periodo.de("2026-11"));
    }
}
