package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ConfirmarPagoUseCase;
import com.condohub.gestion.application.port.in.RechazarPagoUseCase;
import com.condohub.gestion.domain.model.Pago;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolvers de {@code confirmarPago} y {@code rechazarPago}. Sin lógica de negocio: delegan en los casos de uso. */
@Controller
class RevisionPagosMutationController {

    private final ConfirmarPagoUseCase confirmarPago;
    private final RechazarPagoUseCase rechazarPago;

    RevisionPagosMutationController(ConfirmarPagoUseCase confirmarPago, RechazarPagoUseCase rechazarPago) {
        this.confirmarPago = confirmarPago;
        this.rechazarPago = rechazarPago;
    }

    record PagoRevisadoDto(
            UUID id, UUID unidadId, BigDecimal monto, String fechaPago, String estado, String motivoRechazo) {

        static PagoRevisadoDto de(Pago p, String motivoRechazo) {
            return new PagoRevisadoDto(
                    p.id(), p.unidadId(), p.monto().monto(), p.fechaPago().toString(), p.estado().name(), motivoRechazo);
        }
    }

    record ConfirmacionPagoDto(PagoRevisadoDto pago, long reciboNumero, BigDecimal imputado, BigDecimal saldoAFavor) {}

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    ConfirmacionPagoDto confirmarPago(@Argument UUID pagoId, Authentication autenticacion) {
        var r = confirmarPago.confirmar(autenticacion.getName(), pagoId);
        return new ConfirmacionPagoDto(
                PagoRevisadoDto.de(r.pago(), null), r.reciboNumero(), r.imputado().monto(), r.saldoAFavor().monto());
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    PagoRevisadoDto rechazarPago(@Argument UUID pagoId, @Argument String motivo, Authentication autenticacion) {
        var r = rechazarPago.rechazar(autenticacion.getName(), pagoId, motivo);
        return PagoRevisadoDto.de(r.pago(), r.motivo());
    }
}
