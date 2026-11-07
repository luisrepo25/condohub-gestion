package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import com.condohub.gestion.application.port.in.RegistrarPagoUseCase.Comando;
import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import com.condohub.gestion.application.port.out.ComprobantePagoRepository;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.application.usecase.ConsultarUrlComprobanteService;
import com.condohub.gestion.application.usecase.RegistrarPagoConComprobanteService;
import com.condohub.gestion.application.usecase.SolicitarSubidaComprobanteService;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.EstadoDeCuenta;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F8 (foto del comprobante): subir, enlazar al pago y ver con un enlace temporal; solo ADMIN y la unidad (F1). */
class ComprobanteFotoServiceTest {

    private static final String LLAVE = "3f2b8c1e-7a4d-4e6f-9b0a-1c2d3e4f5a6b";
    private final UUID unidad = UUID.randomUUID();
    private final Solicitante admin = new Solicitante("admin", Set.of(Rol.ADMIN));
    private final Solicitante residente = new Solicitante("res", Set.of(Rol.RESIDENTE));
    private final Solicitante ajeno = new Solicitante("ajeno", Set.of(Rol.RESIDENTE));

    private final EstadoDeCuentaRepository cuentas = id -> Optional.of(new EstadoDeCuenta(
            id, id.equals(unidad) ? "A-101" : "B-202", "A", Dinero.CERO, Dinero.CERO, 0, Dinero.CERO, false));
    private final AlmacenComprobantes almacen = new AlmacenComprobantes() {
        public EnlaceTemporal enlaceParaSubir(String clave, String tipo) {
            return new EnlaceTemporal("https://s3/subir/" + clave, Instant.parse("2026-11-07T12:15:00Z"));
        }

        public EnlaceTemporal enlaceParaVer(String clave) {
            return new EnlaceTemporal("https://s3/ver/" + clave, Instant.parse("2026-11-07T12:15:00Z"));
        }
    };
    private final Repo repo = new Repo();
    private final PoliticaVisibilidad politica = new PoliticaVisibilidad();

    private Set<UUID> unidadesDe(String sub) {
        return sub.equals("res") ? Set.of(unidad) : Set.of();
    }

    private final SolicitarSubidaComprobanteService solicitar =
            new SolicitarSubidaComprobanteService(almacen, cuentas, this::unidadesDe, politica);
    private final ConsultarUrlComprobanteService ver =
            new ConsultarUrlComprobanteService(repo, almacen, this::unidadesDe, politica);

    @Test
    void F8_el_residente_pide_subir_la_foto_a_la_carpeta_de_su_unidad() {
        var subida = solicitar.solicitar(residente, unidad, LLAVE, "image/jpeg");

        assertThat(subida.clave()).isEqualTo("comprobantes/A-101/" + LLAVE + ".jpg");
        assertThat(subida.url()).isEqualTo("https://s3/subir/" + subida.clave());
        assertThat(subida.expiraEn()).isEqualTo("2026-11-07T12:15:00Z");
    }

    @Test
    void F1_un_residente_no_sube_fotos_a_la_carpeta_de_otra_unidad() {
        assertThatThrownBy(() -> solicitar.solicitar(ajeno, unidad, LLAVE, "image/jpeg"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void O1_la_clave_de_la_foto_exige_una_idempotency_key_valida() {
        assertThatThrownBy(() -> solicitar.solicitar(residente, unidad, "no-es-uuid", "image/jpeg"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("O1"));
    }

    @Test
    void F8_al_registrar_con_foto_el_pago_queda_enlazado_a_ella() {
        var servicio = new RegistrarPagoConComprobanteService(new Base(), repo, cuentas);
        String clave = "comprobantes/A-101/" + LLAVE + ".jpg";

        var pago = servicio.registrar(residente, comando(clave));

        assertThat(repo.claves).containsEntry(pago.id(), clave);
    }

    @Test
    void F8_sin_foto_el_pago_se_registra_igual_y_no_enlaza_nada() {
        var servicio = new RegistrarPagoConComprobanteService(new Base(), repo, cuentas);

        servicio.registrar(residente, comando(null));

        assertThat(repo.claves).isEmpty();
    }

    @Test
    void F8_no_se_enlaza_una_foto_de_la_carpeta_de_otra_unidad() {
        var servicio = new RegistrarPagoConComprobanteService(new Base(), repo, cuentas);

        assertThatThrownBy(() -> servicio.registrar(residente, comando("comprobantes/B-202/" + LLAVE + ".jpg")))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
        assertThat(repo.claves).isEmpty();
    }

    @Test
    void F8_el_admin_y_la_unidad_ven_la_foto_con_un_enlace_temporal() {
        UUID pago = repo.pagoConFoto(unidad, "comprobantes/A-101/x.jpg");

        assertThat(ver.urlDe(admin, pago)).contains("https://s3/ver/comprobantes/A-101/x.jpg");
        assertThat(ver.urlDe(residente, pago)).contains("https://s3/ver/comprobantes/A-101/x.jpg");
    }

    @Test
    void F1_otro_residente_no_ve_la_foto() {
        UUID pago = repo.pagoConFoto(unidad, "comprobantes/A-101/x.jpg");

        assertThatThrownBy(() -> ver.urlDe(ajeno, pago))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void F8_un_pago_sin_foto_devuelve_vacio_y_uno_inexistente_no_se_encuentra() {
        UUID sinFoto = repo.pagoSinFoto(unidad);

        assertThat(ver.urlDe(admin, sinFoto)).isEmpty();
        assertThatThrownBy(() -> ver.urlDe(admin, UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    private Comando comando(String comprobanteKey) {
        return new Comando(unidad, new BigDecimal("300.00"), "2026-11-06", "BNB", LLAVE, null, null, comprobanteKey);
    }

    /** Caso de uso base falso: devuelve un pago nuevo. */
    private static final class Base implements RegistrarPagoUseCase {
        public Pago registrar(Solicitante solicitante, Comando comando) {
            return new Pago(UUID.randomUUID(), comando.unidadId(), Dinero.de(comando.monto()),
                    LocalDate.parse("2026-11-06"), EstadoPago.PENDIENTE);
        }
    }

    private static final class Repo implements ComprobantePagoRepository {
        final Map<UUID, String> claves = new HashMap<>();
        final Map<UUID, UUID> unidades = new HashMap<>();

        UUID pagoConFoto(UUID unidad, String clave) {
            UUID id = pagoSinFoto(unidad);
            claves.put(id, clave);
            return id;
        }

        UUID pagoSinFoto(UUID unidad) {
            UUID id = UUID.randomUUID();
            unidades.put(id, unidad);
            return id;
        }

        public Optional<ComprobanteDePago> porPago(UUID pagoId) {
            return Optional.ofNullable(unidades.get(pagoId)).map(u -> new ComprobanteDePago(u, claves.get(pagoId)));
        }

        public void enlazar(UUID pagoId, String clave) {
            claves.put(pagoId, clave);
        }
    }
}
