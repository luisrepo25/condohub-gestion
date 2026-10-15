package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ConsultarEstadoDeCuentaUseCase;
import com.condohub.gestion.application.port.in.ListarPagosPendientesUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * Resolvers de lectura de Finanzas. Sin lógica de negocio: verifican el rol, arman el
 * {@code Solicitante} y llaman al caso de uso (la regla F1 vive en el dominio).
 */
@Controller
class FinanzasQueryController {

    private final ConsultarEstadoDeCuentaUseCase consultarEstadoDeCuenta;
    private final ListarPagosPendientesUseCase listarPagosPendientes;

    FinanzasQueryController(
            ConsultarEstadoDeCuentaUseCase consultarEstadoDeCuenta, ListarPagosPendientesUseCase listarPagosPendientes) {
        this.consultarEstadoDeCuenta = consultarEstadoDeCuenta;
        this.listarPagosPendientes = listarPagosPendientes;
    }

    record SaludDto(String servicio, String estado) {}

    record EstadoDeCuentaDto(
            UUID unidadId,
            String codigo,
            String bloque,
            BigDecimal deudaTotal,
            BigDecimal deudaVencida,
            long expensasVencidas,
            BigDecimal saldoAFavor,
            boolean moroso) {}

    record PagoDto(UUID id, UUID unidadId, BigDecimal monto, String fechaPago, String estado) {}

    @QueryMapping
    SaludDto salud() {
        return new SaludDto("gestion", "OK");
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE')")
    EstadoDeCuentaDto estadoDeCuenta(@Argument UUID unidadId, Authentication autenticacion) {
        var e = consultarEstadoDeCuenta.consultar(SolicitanteMapper.desde(autenticacion), unidadId);
        return new EstadoDeCuentaDto(
                e.unidadId(),
                e.codigo(),
                e.bloque(),
                e.deudaTotal().monto(),
                e.deudaVencida().monto(),
                e.expensasVencidas(),
                e.saldoAFavor().monto(),
                e.moroso());
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<PagoDto> pagosPendientes() {
        return listarPagosPendientes.listar().stream()
                .map(p -> new PagoDto(p.id(), p.unidadId(), p.monto().monto(), p.fechaPago().toString(), p.estado().name()))
                .toList();
    }
}