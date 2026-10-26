package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ProponerRecargoUseCase;
import com.condohub.gestion.application.port.in.ResolverRecargoUseCase;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolvers de recargos por mora (F5). Sin lógica de negocio: delegan en los casos de uso. Solo ADMIN. */
@Controller
class RecargosController {

    private final ProponerRecargoUseCase proponerRecargo;
    private final ResolverRecargoUseCase resolverRecargo;

    RecargosController(ProponerRecargoUseCase proponerRecargo, ResolverRecargoUseCase resolverRecargo) {
        this.proponerRecargo = proponerRecargo;
        this.resolverRecargo = resolverRecargo;
    }

    record RecargoPropuestoDto(
            UUID id,
            UUID unidadId,
            UUID cargoOrigenId,
            String periodo,
            BigDecimal porcentaje,
            BigDecimal base,
            BigDecimal monto,
            String estado,
            String origen,
            String motivo,
            UUID cargoGeneradoId) {

        static RecargoPropuestoDto de(RecargoPropuesto r) {
            return new RecargoPropuestoDto(
                    r.id(),
                    r.unidadId(),
                    r.cargoOrigenId(),
                    r.periodo().toString(),
                    r.porcentaje(),
                    r.base().monto(),
                    r.monto().monto(),
                    r.estado().name(),
                    r.origen(),
                    r.motivo(),
                    r.cargoGeneradoId());
        }
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<RecargoPropuestoDto> recargosPropuestos() {
        return resolverRecargo.pendientes().stream().map(RecargoPropuestoDto::de).toList();
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<RecargoPropuestoDto> proponerRecargo(
            @Argument UUID unidadId, @Argument BigDecimal monto, @Argument String motivo, @Argument String misionId) {
        return proponerRecargo.proponer(new ProponerRecargoUseCase.Comando(unidadId, monto, motivo, misionId)).stream()
                .map(RecargoPropuestoDto::de)
                .toList();
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    RecargoPropuestoDto confirmarRecargo(@Argument UUID propuestaId, Authentication autenticacion) {
        return RecargoPropuestoDto.de(resolverRecargo.confirmar(autenticacion.getName(), propuestaId));
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    RecargoPropuestoDto rechazarRecargo(
            @Argument UUID propuestaId, @Argument String motivo, Authentication autenticacion) {
        return RecargoPropuestoDto.de(resolverRecargo.rechazar(autenticacion.getName(), propuestaId, motivo));
    }
}
