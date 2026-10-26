package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolver de {@code registrarPago}. Sin lógica de negocio: delega en el caso de uso. */
@Controller
class PagosMutationController {

    private final RegistrarPagoUseCase registrarPago;

    PagosMutationController(RegistrarPagoUseCase registrarPago) {
        this.registrarPago = registrarPago;
    }

    record PagoDto(UUID id, UUID unidadId, BigDecimal monto, String fechaPago, String estado) {}

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE')")
    PagoDto registrarPago(
            @Argument UUID unidadId,
            @Argument BigDecimal monto,
            @Argument String fechaPago,
            @Argument String banco,
            @Argument String idempotencyKey,
            @Argument String horaDispositivo,
            Authentication autenticacion) {
        var p = registrarPago.registrar(
                SolicitanteMapper.desde(autenticacion),
                new RegistrarPagoUseCase.Comando(unidadId, monto, fechaPago, banco, idempotencyKey, horaDispositivo));
        return new PagoDto(p.id(), p.unidadId(), p.monto().monto(), p.fechaPago().toString(), p.estado().name());
    }
}
