package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.CargoVencido;
import com.condohub.gestion.domain.model.EstadoRecargo;
import com.condohub.gestion.domain.model.RecargoCalculado;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import com.condohub.gestion.domain.service.CalculadoraRecargos;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalculadoraRecargosTest {

    private final CalculadoraRecargos calculadora = new CalculadoraRecargos();
    private final BigDecimal dosPorCiento = new BigDecimal("2.00");

    @Test
    void F5_recargo_es_2_por_ciento_del_saldo_vencido_de_cada_cargo() {
        var agosto = new CargoVencido(UUID.randomUUID(), Dinero.de("390.00"));
        var septiembre = new CargoVencido(UUID.randomUUID(), Dinero.de("150.00"));

        var recargos = calculadora.calcular(dosPorCiento, List.of(agosto, septiembre));

        assertThat(recargos).containsExactly(
                new RecargoCalculado(agosto.id(), Dinero.de("390.00"), Dinero.de("7.80")),
                new RecargoCalculado(septiembre.id(), Dinero.de("150.00"), Dinero.de("3.00")));
    }

    @Test
    void F5_el_recargo_se_redondea_a_2_decimales() {
        var cargo = new CargoVencido(UUID.randomUUID(), Dinero.de("292.50"));

        // 292.50 × 2 % = 5.85
        assertThat(calculadora.calcular(dosPorCiento, List.of(cargo)).get(0).monto()).isEqualTo(Dinero.de("5.85"));
        // 0.25 × 2 % = 0.005 → 0.01
        var chico = new CargoVencido(UUID.randomUUID(), Dinero.de("0.25"));
        assertThat(calculadora.calcular(dosPorCiento, List.of(chico)).get(0).monto()).isEqualTo(Dinero.de("0.01"));
    }

    @Test
    void F5_un_recargo_que_redondea_a_cero_no_se_propone() {
        var cargo = new CargoVencido(UUID.randomUUID(), Dinero.de("0.10"));

        assertThat(calculadora.calcular(dosPorCiento, List.of(cargo))).isEmpty();
    }

    @Test
    void F5_el_porcentaje_sale_del_parametro_y_debe_ser_mayor_a_cero() {
        var cargo = new CargoVencido(UUID.randomUUID(), Dinero.de("100.00"));

        assertThat(calculadora.calcular(new BigDecimal("3.00"), List.of(cargo)).get(0).monto())
                .isEqualTo(Dinero.de("3.00"));
        assertThatThrownBy(() -> calculadora.calcular(BigDecimal.ZERO, List.of(cargo)))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F5"));
    }

    @Test
    void F5_el_monto_que_pide_el_agente_debe_coincidir_con_el_calculado() {
        calculadora.exigirMontoSolicitado(Dinero.de("7.80"), Dinero.de("7.80"));

        assertThatThrownBy(() -> calculadora.exigirMontoSolicitado(Dinero.de("50.00"), Dinero.de("7.80")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F5"));
    }

    @Test
    void F5_un_recargo_ya_resuelto_no_se_confirma_ni_se_rechaza() {
        var confirmado = recargo(EstadoRecargo.CONFIRMADO);

        assertThatThrownBy(confirmado::confirmar)
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F5"));
        assertThatThrownBy(() -> confirmado.rechazar("x")).isInstanceOf(ReglaNegocioVioladaException.class);
        assertThat(recargo(EstadoRecargo.PROPUESTO).rechazar("  No corresponde  ")).isEqualTo("No corresponde");
    }

    private static RecargoPropuesto recargo(EstadoRecargo estado) {
        return new RecargoPropuesto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Periodo.de("2026-10"),
                new BigDecimal("2.00"),
                Dinero.de("390.00"),
                Dinero.de("7.80"),
                estado,
                "ADMIN",
                null,
                null);
    }
}
