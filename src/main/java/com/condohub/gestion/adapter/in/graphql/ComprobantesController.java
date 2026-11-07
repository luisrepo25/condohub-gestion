package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.ConsultarUrlComprobanteUseCase;
import com.condohub.gestion.application.port.in.SolicitarSubidaComprobanteUseCase;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolvers de la foto del comprobante (F8). Sin lógica de negocio: delegan en los casos de uso. */
@Controller
class ComprobantesController {

    private final SolicitarSubidaComprobanteUseCase solicitarSubida;
    private final ConsultarUrlComprobanteUseCase consultarUrl;

    ComprobantesController(SolicitarSubidaComprobanteUseCase solicitarSubida, ConsultarUrlComprobanteUseCase consultarUrl) {
        this.solicitarSubida = solicitarSubida;
        this.consultarUrl = consultarUrl;
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE')")
    SolicitarSubidaComprobanteUseCase.SubidaComprobante solicitarSubidaComprobante(
            @Argument UUID unidadId,
            @Argument String idempotencyKey,
            @Argument String tipoContenido,
            Authentication autenticacion) {
        return solicitarSubida.solicitar(SolicitanteMapper.desde(autenticacion), unidadId, idempotencyKey, tipoContenido);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENTE')")
    String urlComprobante(@Argument UUID pagoId, Authentication autenticacion) {
        return consultarUrl.urlDe(SolicitanteMapper.desde(autenticacion), pagoId).orElse(null);
    }
}
