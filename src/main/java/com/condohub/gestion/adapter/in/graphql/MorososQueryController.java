package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ConsultarMorososUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/** Resolver de {@code morosos}. Sin lógica de negocio: solo ADMIN (incluida la cuenta de servicio de n8n y Agentes). */
@Controller
class MorososQueryController {

    private final ConsultarMorososUseCase consultarMorosos;

    MorososQueryController(ConsultarMorososUseCase consultarMorosos) {
        this.consultarMorosos = consultarMorosos;
    }

    record MorosoDto(
            UUID unidadId,
            String codigo,
            String bloque,
            BigDecimal deudaVencida,
            long expensasVencidas,
            String propietarioNombre,
            String propietarioEmail,
            String propietarioTelefono) {}

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<MorosoDto> morosos() {
        return consultarMorosos.consultar().stream()
                .map(m -> new MorosoDto(
                        m.unidadId(),
                        m.codigo(),
                        m.bloque(),
                        m.deudaVencida().monto(),
                        m.expensasVencidas(),
                        m.propietarioNombre(),
                        m.propietarioEmail(),
                        m.propietarioTelefono()))
                .toList();
    }
}