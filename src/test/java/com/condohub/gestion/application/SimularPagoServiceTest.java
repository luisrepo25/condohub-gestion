package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.out.CargosDeUnidadRepository;
import com.condohub.gestion.application.port.out.CargosDeUnidadRepository.CargoDeUnidad;
import com.condohub.gestion.application.usecase.SimularPagoService;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F7: la vista previa reparte el pago con la misma regla FIFO de la confirmación y no guarda nada. */
class SimularPagoServiceTest {

    private final UUID miUnidad = UUID.randomUUID();
    private final UUID otraUnidad = UUID.randomUUID();
    private final Solicitante residente = new Solicitante("sub-res", Set.of(Rol.RESIDENTE));
    private final Solicitante admin = new Solicitante("sub-admin", Set.of(Rol.ADMIN));
    private final CargosEnMemoria cargos = new CargosEnMemoria(Set.of(miUnidad, otraUnidad));
    private final SimularPagoService servicio =
            new SimularPagoService(cargos, sub -> Set.of(miUnidad), new PoliticaVisibilidad(), new ImputadorPagos());

    private CargoDeUnidad cargo(String tipo, String concepto, String saldo, String vence, String creado) {
        return new CargoDeUnidad(
                UUID.randomUUID(), tipo, concepto, Dinero.de(saldo), LocalDate.parse(vence), Instant.parse(creado));
    }

    @Test
    void F7_un_pago_menor_al_cargo_mas_antiguo_lo_reduce_y_no_toca_los_demas() {
        cargos.agregar(miUnidad, cargo("USO_AREA", "Uso de SALON", "500.00", "2026-12-10", "2026-11-07T01:00:00Z"));
        cargos.agregar(miUnidad, cargo("GARANTIA", "Garantía de SALON", "300.00", "2026-12-10", "2026-11-07T01:00:01Z"));

        var sim = servicio.simular(residente, miUnidad, new BigDecimal("300.00"));

        assertThat(sim.lineas()).singleElement().satisfies(l -> {
            assertThat(l.concepto()).isEqualTo("Uso de SALON");
            assertThat(l.saldoActual()).isEqualTo(Dinero.de("500.00"));
            assertThat(l.aplicado()).isEqualTo(Dinero.de("300.00"));
            assertThat(l.saldoResultante()).isEqualTo(Dinero.de("200.00"));
        });
        assertThat(sim.excedente()).isEqualTo(Dinero.CERO);
    }

    @Test
    void F7_los_cargos_vencidos_se_pagan_antes_que_los_que_aun_no_vencen() {
        cargos.agregar(miUnidad, cargo("EXPENSA", "Expensa 11/2026", "270.00", "2026-11-10", "2026-11-01T00:00:00Z"));
        cargos.agregar(miUnidad, cargo("EXPENSA", "Expensa 09/2026", "270.00", "2026-09-10", "2026-09-01T00:00:00Z"));

        var sim = servicio.simular(residente, miUnidad, new BigDecimal("270.00"));

        assertThat(sim.lineas()).singleElement().satisfies(l -> {
            assertThat(l.concepto()).isEqualTo("Expensa 09/2026");
            assertThat(l.saldoResultante()).isEqualTo(Dinero.CERO);
        });
    }

    @Test
    void F7_un_pago_mayor_salda_varios_cargos_en_orden_y_reparte_el_excedente_como_saldo_a_favor() {
        cargos.agregar(miUnidad, cargo("EXPENSA", "Expensa 09/2026", "270.00", "2026-09-10", "2026-09-01T00:00:00Z"));
        cargos.agregar(miUnidad, cargo("EXPENSA", "Expensa 10/2026", "270.00", "2026-10-10", "2026-10-01T00:00:00Z"));

        var sim = servicio.simular(residente, miUnidad, new BigDecimal("700.00"));

        assertThat(sim.lineas()).extracting(l -> l.concepto()).containsExactly("Expensa 09/2026", "Expensa 10/2026");
        assertThat(sim.lineas()).allSatisfy(l -> assertThat(l.saldoResultante()).isEqualTo(Dinero.CERO));
        assertThat(sim.excedente()).isEqualTo(Dinero.de("160.00"));
    }

    @Test
    void F7_sin_cargos_pendientes_todo_el_pago_seria_saldo_a_favor() {
        var sim = servicio.simular(residente, miUnidad, new BigDecimal("300.00"));

        assertThat(sim.lineas()).isEmpty();
        assertThat(sim.excedente()).isEqualTo(Dinero.de("300.00"));
    }

    @Test
    void F9_simular_no_guarda_ni_cambia_nada() {
        cargos.agregar(miUnidad, cargo("EXPENSA", "Expensa 09/2026", "270.00", "2026-09-10", "2026-09-01T00:00:00Z"));

        servicio.simular(residente, miUnidad, new BigDecimal("100.00"));
        servicio.simular(residente, miUnidad, new BigDecimal("100.00"));

        assertThat(cargos.pendientes(miUnidad)).singleElement().satisfies(c -> assertThat(c.saldo()).isEqualTo(Dinero.de("270.00")));
    }

    @Test
    void F1_el_residente_no_simula_pagos_de_otra_unidad() {
        assertThatThrownBy(() -> servicio.simular(residente, otraUnidad, new BigDecimal("10.00")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void F1_el_admin_simula_pagos_de_cualquier_unidad() {
        var sim = servicio.simular(admin, otraUnidad, new BigDecimal("10.00"));

        assertThat(sim.excedente()).isEqualTo(Dinero.de("10.00"));
    }

    @Test
    void F10_el_monto_debe_ser_mayor_a_cero_y_de_hasta_dos_decimales() {
        for (var invalido : List.of("0", "-5", "10.123")) {
            assertThatThrownBy(() -> servicio.simular(residente, miUnidad, new BigDecimal(invalido)))
                    .isInstanceOfSatisfying(
                            ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F10"));
        }
    }

    @Test
    void una_unidad_inexistente_responde_no_encontrada() {
        var fantasma = UUID.randomUUID();
        var admin2 = new SimularPagoService(cargos, sub -> Set.of(), new PoliticaVisibilidad(), new ImputadorPagos());

        assertThatThrownBy(() -> admin2.simular(admin, fantasma, new BigDecimal("10.00")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static final class CargosEnMemoria implements CargosDeUnidadRepository {
        private final Set<UUID> unidades;
        private final List<Object[]> filas = new ArrayList<>();

        CargosEnMemoria(Set<UUID> unidades) {
            this.unidades = unidades;
        }

        void agregar(UUID unidad, CargoDeUnidad cargo) {
            filas.add(new Object[] {unidad, cargo});
        }

        @Override
        public List<CargoDeUnidad> pendientes(UUID unidadId) {
            return filas.stream()
                    .filter(f -> f[0].equals(unidadId))
                    .map(f -> (CargoDeUnidad) f[1])
                    .toList();
        }

        @Override
        public boolean existeUnidad(UUID unidadId) {
            return unidades.contains(unidadId);
        }
    }
}
