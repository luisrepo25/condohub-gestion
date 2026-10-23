package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.port.out.RevisionPagoRepository;
import com.condohub.gestion.application.usecase.ConfirmarPagoService;
import com.condohub.gestion.application.usecase.RechazarPagoService;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.PagoPorRevisar;
import com.condohub.gestion.domain.model.ResultadoImputacion;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionPagoServiceTest {

    private final UUID unidad = UUID.randomUUID();
    private final PagosEnRevision repo = new PagosEnRevision();
    private final ConfirmarPagoService confirmar = new ConfirmarPagoService(repo, new ImputadorPagos());
    private final RechazarPagoService rechazar = new RechazarPagoService(repo);

    @Test
    void F7_F8_confirmar_imputa_emite_recibo_y_devuelve_el_saldo_a_favor() {
        var cargo = new CargoPendiente(
                UUID.randomUUID(), Dinero.de("300"), LocalDate.parse("2026-09-10"), Instant.parse("2026-09-01T00:00:00Z"));
        repo.cargos.add(cargo);
        var pagoId = repo.nuevoPago("350.00");

        var confirmado = confirmar.confirmar("sub-admin", pagoId);

        assertThat(confirmado.pago().estado()).isEqualTo(EstadoPago.CONFIRMADO);
        assertThat(confirmado.reciboNumero()).isEqualTo(1);
        assertThat(confirmado.imputado()).isEqualTo(Dinero.de("300"));
        assertThat(confirmado.saldoAFavor()).isEqualTo(Dinero.de("50"));
        assertThat(repo.confirmadoPor).isEqualTo("sub-admin");
    }

    @Test
    void F8_un_pago_ya_confirmado_no_se_confirma_otra_vez() {
        var pagoId = repo.nuevoPago("100.00");
        confirmar.confirmar("sub-admin", pagoId);

        assertThatThrownBy(() -> confirmar.confirmar("sub-admin", pagoId))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void F8_rechazar_exige_motivo() {
        var pagoId = repo.nuevoPago("100.00");

        assertThatThrownBy(() -> rechazar.rechazar("sub-admin", pagoId, "   "))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
        assertThat(repo.pagos.get(pagoId).estado()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void F8_rechazar_guarda_el_motivo_y_un_rechazado_ya_no_se_confirma() {
        var pagoId = repo.nuevoPago("100.00");

        var rechazado = rechazar.rechazar("sub-admin", pagoId, "  El monto no figura en el extracto ");

        assertThat(rechazado.pago().estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(rechazado.motivo()).isEqualTo("El monto no figura en el extracto");
        assertThatThrownBy(() -> confirmar.confirmar("sub-admin", pagoId))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void confirmar_un_pago_que_no_existe_es_NOT_FOUND() {
        assertThatThrownBy(() -> confirmar.confirmar("sub-admin", UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private final class PagosEnRevision implements RevisionPagoRepository {

        final Map<UUID, PagoPorRevisar> pagos = new HashMap<>();
        final List<CargoPendiente> cargos = new ArrayList<>();
        String confirmadoPor;
        long recibos;

        UUID nuevoPago(String monto) {
            var id = UUID.randomUUID();
            pagos.put(id, new PagoPorRevisar(id, unidad, Dinero.de(monto), EstadoPago.PENDIENTE));
            return id;
        }

        @Override
        public Optional<PagoPorRevisar> porIdParaRevisar(UUID pagoId) {
            return Optional.ofNullable(pagos.get(pagoId));
        }

        @Override
        public List<CargoPendiente> cargosPendientes(UUID unidadId) {
            return cargos;
        }

        @Override
        public long confirmar(UUID pagoId, String adminSub, ResultadoImputacion resultado) {
            var p = pagos.get(pagoId);
            pagos.put(pagoId, new PagoPorRevisar(p.id(), p.unidadId(), p.monto(), EstadoPago.CONFIRMADO));
            confirmadoPor = adminSub;
            return ++recibos;
        }

        @Override
        public void rechazar(UUID pagoId, String adminSub, String motivo) {
            var p = pagos.get(pagoId);
            pagos.put(pagoId, new PagoPorRevisar(p.id(), p.unidadId(), p.monto(), EstadoPago.RECHAZADO));
        }

        @Override
        public Pago pago(UUID pagoId) {
            var p = pagos.get(pagoId);
            return new Pago(p.id(), p.unidadId(), p.monto(), LocalDate.parse("2026-10-20"), p.estado());
        }
    }
}
