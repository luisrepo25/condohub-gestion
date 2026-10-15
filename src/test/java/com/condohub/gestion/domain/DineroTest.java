package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DineroTest {

    @Test
    void F10_dinero_siempre_tiene_dos_decimales() {
        assertThat(Dinero.de("390").toString()).isEqualTo("390.00");
        assertThat(Dinero.de(new BigDecimal("0.5")).toString()).isEqualTo("0.50");
    }

    @Test
    void F10_dinero_rechaza_mas_de_dos_decimales_en_vez_de_redondear() {
        assertThatThrownBy(() -> Dinero.de("10.005"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F10"));
    }

    @Test
    void F10_dinero_no_puede_ser_negativo() {
        assertThatThrownBy(() -> Dinero.de("-1.00")).isInstanceOf(ReglaNegocioVioladaException.class);
    }

    @Test
    void F10_dinero_se_compara_por_valor() {
        assertThat(Dinero.de("5")).isEqualTo(Dinero.de("5.00"));
    }
}