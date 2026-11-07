package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.SimularPagoUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolver de {@code simularPago}. Sin lógica de negocio: delega en el caso de uso (solo lectura). */
@Controller
class SimulacionPagoQueryController {

    private final SimularPagoUseCase simularPago;

    SimulacionPagoQueryController(SimularPagoUseCase simularPago) {
        this.simularPago = simularPago;
    }

    record LineaDto(
            UUID cargoId,
            String tipo,
            String concepto,
            String fechaVencimiento,
            BigDecimal saldoActual,
            BigDecimal aplicado,
            BigDecimal saldoResultante) {}

    record SimulacionDto(List<LineaDto> lineas, BigDecimal excedente) {}

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE')")
    SimulacionDto simularPago(@Argument UUID unidadId, @Argument BigDecimal monto, Authentication autenticacion) {
        var s = simularPago.simular(SolicitanteMapper.desde(autenticacion), unidadId, monto);
        return new SimulacionDto(
                s.lineas().stream()
                        .map(l -> new LineaDto(
                                l.cargoId(),
                                l.tipo(),
                                l.concepto(),
                                l.fechaVencimiento().toString(),
                                l.saldoActual().monto(),
                                l.aplicado().monto(),
                                l.saldoResultante().monto()))
                        .toList(),
                s.excedente().monto());
    }
}
