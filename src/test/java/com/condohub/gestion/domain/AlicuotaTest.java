package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Alicuota;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlicuotaTest {

    @Test
    void F2_alicuotas_que_suman_100_cuadran() {
        var alicuotas = List.of(Alicuota.de("33.3333"), Alicuota.de("33.3333"), Alicuota.de("33.3334"));

        assertThat(Alicuota.sumanCien(alicuotas)).isTrue();
    }

    @Test
    void F2_alicuotas_que_no_suman_100_no_permiten_emitir() {
        var alicuotas = List.of(Alicuota.de("50"), Alicuota.de("49.9999"));

        assertThat(Alicuota.sumanCien(alicuotas)).isFalse();
        assertThatThrownBy(() -> Alicuota.exigirQueSumenCien(alicuotas))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F2"));
    }

    @Test
    void F2_sin_unidades_activas_no_se_puede_emitir() {
        assertThatThrownBy(() -> Alicuota.exigirQueSumenCien(List.of()))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F2"));
    }

    @Test
    void F2_alicuota_debe_ser_mayor_a_cero_y_como_maximo_100() {
        assertThatThrownBy(() -> Alicuota.de("0")).isInstanceOf(ReglaNegocioVioladaException.class);
        assertThatThrownBy(() -> Alicuota.de("-1")).isInstanceOf(ReglaNegocioVioladaException.class);
        assertThatThrownBy(() -> Alicuota.de("100.0001")).isInstanceOf(ReglaNegocioVioladaException.class);
        assertThat(Alicuota.de("100").toString()).isEqualTo("100.0000");
    }

    @Test
    void F2_alicuota_admite_como_maximo_4_decimales_en_vez_de_redondear() {
        assertThatThrownBy(() -> Alicuota.de("0.12345"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F2"));
    }

    @Test
    void F2_alicuota_se_compara_por_valor() {
        assertThat(Alicuota.de("0.5")).isEqualTo(Alicuota.de("0.5000"));
    }
}
