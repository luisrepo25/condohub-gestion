package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.domain.model.AlcanceAnulacion;
import com.condohub.gestion.domain.model.CargoDeReserva;
import com.condohub.gestion.domain.service.CargosDeReserva;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CargosDeReservaTest {

    private final CargosDeReserva cargos = new CargosDeReserva();
    private final LocalDate hoy = LocalDate.parse("2026-10-27");

    @Test
    void R5_el_salon_genera_costo_de_uso_y_garantia() {
        var generados = cargos.para("SALON", Dinero.de("500.00"), Dinero.de("300.00"), hoy, 10);

        assertThat(generados).extracting(CargoDeReserva::tipo).containsExactly(CargoDeReserva.Tipo.USO_AREA, CargoDeReserva.Tipo.GARANTIA);
        assertThat(generados).extracting(CargoDeReserva::monto).containsExactly(Dinero.de("500.00"), Dinero.de("300.00"));
        assertThat(generados.get(0).concepto()).isEqualTo("Uso de SALON");
        assertThat(generados.get(1).concepto()).isEqualTo("Garantía de SALON");
    }

    @Test
    void R5_las_demas_areas_solo_costo_de_uso_y_un_monto_cero_no_genera_cargo() {
        assertThat(cargos.para("CHURR-1", Dinero.de("50.00"), Dinero.de("0"), hoy, 10))
                .extracting(CargoDeReserva::tipo)
                .containsExactly(CargoDeReserva.Tipo.USO_AREA);
        assertThat(cargos.para("PISCINA", Dinero.de("0.00"), Dinero.de("0.00"), hoy, 10)).isEmpty();
    }

    @Test
    void los_cargos_de_reserva_vencen_el_dia_de_vencimiento_del_mes_siguiente() {
        assertThat(cargos.para("SALON", Dinero.de("500"), Dinero.de("300"), hoy, 10))
                .extracting(CargoDeReserva::fechaVencimiento)
                .containsOnly(LocalDate.parse("2026-11-10"));
        assertThat(cargos.para("SALON", Dinero.de("500"), Dinero.de("0"), LocalDate.parse("2026-12-20"), 10).get(0).fechaVencimiento())
                .isEqualTo(LocalDate.parse("2027-01-10"));
    }

    @Test
    void R6_R7_el_alcance_dice_que_cargos_se_anulan() {
        assertThat(AlcanceAnulacion.TODOS.incluye(CargoDeReserva.Tipo.USO_AREA)).isTrue();
        assertThat(AlcanceAnulacion.TODOS.incluye(CargoDeReserva.Tipo.GARANTIA)).isTrue();
        assertThat(AlcanceAnulacion.SOLO_GARANTIA.incluye(CargoDeReserva.Tipo.USO_AREA)).isFalse();
        assertThat(AlcanceAnulacion.SOLO_GARANTIA.incluye(CargoDeReserva.Tipo.GARANTIA)).isTrue();
    }
}
