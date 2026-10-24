package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase.Comando;
import com.condohub.gestion.application.port.out.RegistroPagoRepository;
import com.condohub.gestion.application.usecase.RegistrarPagoService;
import com.condohub.gestion.domain.event.EventoFinanzas;
import com.condohub.gestion.domain.event.PagoRegistrado;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.RegistroDePago;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrarPagoServiceTest {

    private final UUID miUnidad = UUID.randomUUID();
    private final UUID otraUnidad = UUID.randomUUID();
    private final PagosEnMemoria pagos = new PagosEnMemoria(Set.of(miUnidad, otraUnidad));
    /** "Hoy" es el 22/10/2026. */
    private final Clock hoy = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);

    private final List<EventoFinanzas> eventos = new ArrayList<>();

    private final RegistrarPagoService servicio =
            new RegistrarPagoService(pagos, sub -> Set.of(miUnidad), new PoliticaVisibilidad(), hoy, eventos::add);

    @Test
    void S4_registrar_un_pago_publica_PagoRegistrado_con_sus_datos() {
        var pago = servicio.registrar(residente, comando(miUnidad, "390.00", UUID.randomUUID().toString()));

        assertThat(eventos).singleElement().isInstanceOfSatisfying(PagoRegistrado.class, e -> {
            assertThat(e.pagoId()).isEqualTo(pago.id());
            assertThat(e.unidadId()).isEqualTo(miUnidad);
            assertThat(e.monto()).isEqualTo(Dinero.de("390.00"));
            assertThat(e.banco()).isEqualTo("Banco Unión");
        });
    }

    @Test
    void S4_O3_reenviar_la_misma_clave_no_publica_otro_evento() {
        var clave = UUID.randomUUID().toString();

        servicio.registrar(residente, comando(miUnidad, "390.00", clave));
        servicio.registrar(residente, comando(miUnidad, "390.00", clave));

        assertThat(eventos).hasSize(1);
    }

    @Test
    void S4_un_pago_rechazado_por_una_regla_no_publica_nada() {
        assertThatThrownBy(() -> servicio.registrar(residente, comando(otraUnidad, "390.00", UUID.randomUUID().toString())))
                .isInstanceOf(ReglaNegocioVioladaException.class);

        assertThat(eventos).isEmpty();
    }

    @Test
    void F8_un_comprobante_con_fecha_futura_se_rechaza() {
        var futuro = new Comando(
                miUnidad, new BigDecimal("390.00"), "2026-10-23", "Banco Unión", UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> servicio.registrar(residente, futuro))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
        assertThat(pagos.guardados).isEmpty();
    }

    @Test
    void F8_un_comprobante_de_hoy_se_acepta() {
        var deHoy = new Comando(
                miUnidad, new BigDecimal("390.00"), "2026-10-22", "Banco Unión", UUID.randomUUID().toString(), null);

        assertThat(servicio.registrar(residente, deHoy).fechaPago()).isEqualTo(java.time.LocalDate.parse("2026-10-22"));
    }

    private final Solicitante residente = new Solicitante("sub-res", Set.of(Rol.RESIDENTE));
    private final Solicitante admin = new Solicitante("sub-admin", Set.of(Rol.ADMIN));

    private Comando comando(UUID unidadId, String monto, String clave) {
        return new Comando(unidadId, new BigDecimal(monto), "2026-10-20", "Banco Unión", clave, "2026-10-20T14:30:00-04:00");
    }

    @Test
    void F9_el_pago_nace_pendiente() {
        var pago = servicio.registrar(residente, comando(miUnidad, "390.00", UUID.randomUUID().toString()));

        assertThat(pago.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.unidadId()).isEqualTo(miUnidad);
        assertThat(pagos.guardados).hasSize(1);
        assertThat(pagos.guardados.get(0).registradoPor()).isEqualTo("sub-res");
        assertThat(pagos.guardados.get(0).horaDispositivo()).isEqualTo(Instant.parse("2026-10-20T18:30:00Z"));
    }

    @Test
    void O3_misma_idempotency_key_devuelve_el_mismo_pago() {
        var clave = UUID.randomUUID().toString();

        var primero = servicio.registrar(residente, comando(miUnidad, "390.00", clave));
        var repetido = servicio.registrar(residente, comando(miUnidad, "390.00", clave));

        assertThat(repetido).isEqualTo(primero);
        assertThat(pagos.guardados).hasSize(1);
    }

    @Test
    void O3_misma_clave_con_otros_datos_devuelve_el_pago_original() {
        var clave = UUID.randomUUID().toString();
        var primero = servicio.registrar(residente, comando(miUnidad, "390.00", clave));

        var repetido = servicio.registrar(residente, comando(miUnidad, "999.00", clave));

        assertThat(repetido).isEqualTo(primero);
        assertThat(pagos.guardados).hasSize(1);
    }

    @Test
    void F1_residente_no_registra_pago_de_otra_unidad() {
        assertThatThrownBy(() -> servicio.registrar(residente, comando(otraUnidad, "390.00", UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
        assertThat(pagos.guardados).isEmpty();
    }

    @Test
    void F1_admin_registra_pago_de_cualquier_unidad() {
        var pago = servicio.registrar(admin, comando(otraUnidad, "100.00", UUID.randomUUID().toString()));

        assertThat(pago.unidadId()).isEqualTo(otraUnidad);
    }

    @Test
    void F10_monto_debe_ser_mayor_a_cero() {
        assertThatThrownBy(() -> servicio.registrar(residente, comando(miUnidad, "0", UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F10"));
    }

    @Test
    void O1_la_clave_debe_ser_un_uuid_v4() {
        assertThatThrownBy(() -> servicio.registrar(residente, comando(miUnidad, "390.00", "no-es-uuid")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("O1"));
        // UUID v1 (basado en tiempo), no v4
        assertThatThrownBy(() -> servicio.registrar(
                        residente, comando(miUnidad, "390.00", "a8098c1a-f86e-11da-bd1a-00112444be1e")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("O1"));
    }

    @Test
    void F8_fecha_del_comprobante_invalida_se_rechaza() {
        var malo = new Comando(miUnidad, new BigDecimal("10"), "20/10/2026", null, UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> servicio.registrar(residente, malo))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void admin_con_unidad_inexistente_recibe_no_encontrado() {
        assertThatThrownBy(() -> servicio.registrar(admin, comando(UUID.randomUUID(), "10", UUID.randomUUID().toString())))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static final class PagosEnMemoria implements RegistroPagoRepository {

        final List<RegistroDePago> guardados = new ArrayList<>();
        private final List<Pago> pagos = new ArrayList<>();
        private final Set<UUID> unidades;

        PagosEnMemoria(Set<UUID> unidades) {
            this.unidades = unidades;
        }

        @Override
        public Optional<Pago> porIdempotencyKey(IdempotencyKey clave) {
            for (int i = 0; i < guardados.size(); i++) {
                if (guardados.get(i).idempotencyKey().equals(clave)) {
                    return Optional.of(pagos.get(i));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean existeUnidad(UUID unidadId) {
            return unidades.contains(unidadId);
        }

        @Override
        public Pago guardar(RegistroDePago registro) {
            var pago = new Pago(
                    UUID.randomUUID(), registro.unidadId(), registro.monto(), registro.fechaPago(), registro.estado());
            guardados.add(registro);
            pagos.add(pago);
            return pago;
        }
    }
}
