package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ConsultarUrlComprobanteUseCase;
import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import com.condohub.gestion.application.port.out.ComprobantePagoRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ConsultarUrlComprobanteService implements ConsultarUrlComprobanteUseCase {

    private final ComprobantePagoRepository comprobantes;
    private final AlmacenComprobantes almacen;
    private final UnidadRepository unidades;
    private final PoliticaVisibilidad politica;

    public ConsultarUrlComprobanteService(
            ComprobantePagoRepository comprobantes,
            AlmacenComprobantes almacen,
            UnidadRepository unidades,
            PoliticaVisibilidad politica) {
        this.comprobantes = comprobantes;
        this.almacen = almacen;
        this.unidades = unidades;
        this.politica = politica;
    }

    @Override
    public Optional<String> urlDe(Solicitante solicitante, UUID pagoId) {
        var comprobante = comprobantes.porPago(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
        Set<UUID> propias = solicitante.tieneRol(Rol.ADMIN) ? Set.of() : unidades.idsDeUnidadesDe(solicitante.sub());
        politica.verificarPuedeVerEstadoDeCuenta(solicitante, comprobante.unidadId(), propias);
        return Optional.ofNullable(comprobante.clave()).map(clave -> almacen.enlaceParaVer(clave).url());
    }
}
