package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase.Comando;
import com.condohub.gestion.application.port.out.RegistroPagoRepository;
import com.condohub.gestion.application.usecase.RegistrarPagoService;
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

/** F8: el mismo comprobante bancario (banco + número de operación) no se registra dos veces. */
class RegistrarPagoOperacionTest {

    private final UUID unidad = UUID.randomUUID();
    private final Solicitante admin = new Solicitante("sub-admin", Set.of(Rol.ADMIN));
    private final Clock hoy = Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC);
    private final PagosConOperacion pagos = new PagosConOperacion();
    private final RegistrarPagoService servicio =
            new RegistrarPagoService(pagos, sub -> Set.of(), new PoliticaVisibilidad(), hoy, evento -> {});

    private Comando comando(String banco, String nroOperacion, String clave) {
        return new Comando(
                unidad, new BigDecimal("300.00"), "2026-10-20", banco, clave, "2026-10-20T14:30:00-04:00", nroOperacion);
    }

    @Test
    void F8_el_mismo_banco_y_numero_de_operacion_no_se_registran_dos_veces() {
        servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
        assertThat(pagos.guardados).hasSize(1);
    }

    @Test
    void F8_el_mismo_numero_en_otro_banco_es_otra_operacion() {
        servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString()));

        servicio.registrar(admin, comando("BNB", "48201937", UUID.randomUUID().toString()));

        assertThat(pagos.guardados).hasSize(2);
    }

    @Test
    void F8_sin_numero_de_operacion_no_hay_control_y_el_pago_se_registra() {
        servicio.registrar(admin, comando("Banco Unión", null, UUID.randomUUID().toString()));
        servicio.registrar(admin, comando("Banco Unión", null, UUID.randomUUID().toString()));

        assertThat(pagos.guardados).hasSize(2);
    }

    @Test
    void F8_el_numero_de_operacion_en_blanco_cuenta_como_ausente() {
        servicio.registrar(admin, comando("Banco Unión", "   ", UUID.randomUUID().toString()));
        servicio.registrar(admin, comando("Banco Unión", "   ", UUID.randomUUID().toString()));

        assertThat(pagos.guardados).hasSize(2);
        assertThat(pagos.guardados.get(0).nroOperacion()).isNull();
    }

    @Test
    void F8_los_espacios_alrededor_del_numero_no_evitan_el_control() {
        servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString()));

        assertThatThrownBy(() -> servicio.registrar(admin, comando("Banco Unión", "  48201937 ", UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void O3_reintentar_la_misma_operacion_con_su_clave_no_es_un_duplicado() {
        var clave = UUID.randomUUID().toString();
        var primero = servicio.registrar(admin, comando("Banco Unión", "48201937", clave));

        var repetido = servicio.registrar(admin, comando("Banco Unión", "48201937", clave));

        assertThat(repetido).isEqualTo(primero);
        assertThat(pagos.guardados).hasSize(1);
    }

    @Test
    void F8_un_pago_rechazado_libera_el_numero_de_operacion() {
        servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString()));
        pagos.rechazarTodos();

        servicio.registrar(admin, comando("Banco Unión", "48201937", UUID.randomUUID().toString()));

        assertThat(pagos.guardados).hasSize(2);
    }

    @Test
    void F8_el_numero_de_operacion_admite_como_maximo_60_caracteres() {
        var largo = "9".repeat(61);

        assertThatThrownBy(() -> servicio.registrar(admin, comando("Banco Unión", largo, UUID.randomUUID().toString())))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void F8_un_registro_sin_numero_de_operacion_sigue_construyendose_como_antes() {
        var registro = new RegistroDePago(
                unidad,
                Dinero.de("10.00"),
                java.time.LocalDate.parse("2026-10-20"),
                "BNB",
                IdempotencyKey.de(UUID.randomUUID().toString()),
                "sub",
                null);

        assertThat(registro.nroOperacion()).isNull();
    }

    /** Repositorio en memoria que respeta la unicidad (banco, número) de pagos no rechazados. */
    private static final class PagosConOperacion implements RegistroPagoRepository {
        final List<RegistroDePago> guardados = new ArrayList<>();
        private final List<RegistroDePago> rechazados = new ArrayList<>();

        void rechazarTodos() {
            rechazados.addAll(guardados);
        }

        @Override
        public Optional<Pago> porIdempotencyKey(IdempotencyKey clave) {
            return guardados.stream().filter(r -> r.idempotencyKey().equals(clave)).findFirst().map(this::pago);
        }

        @Override
        public boolean existeUnidad(UUID unidadId) {
            return true;
        }

        @Override
        public boolean existeOperacion(String banco, String nroOperacion) {
            return guardados.stream()
                    .filter(r -> !rechazados.contains(r))
                    .anyMatch(r -> banco != null
                            && banco.equals(r.banco())
                            && nroOperacion != null
                            && nroOperacion.equals(r.nroOperacion()));
        }

        @Override
        public Pago guardar(RegistroDePago registro) {
            guardados.add(registro);
            return pago(registro);
        }

        private Pago pago(RegistroDePago r) {
            return new Pago(UUID.nameUUIDFromBytes(r.idempotencyKey().valor().toString().getBytes()), r.unidadId(), r.monto(), r.fechaPago(), EstadoPago.PENDIENTE);
        }
    }
}
