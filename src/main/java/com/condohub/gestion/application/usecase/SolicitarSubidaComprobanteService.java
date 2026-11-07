package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.SolicitarSubidaComprobanteUseCase;
import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.ClaveComprobante;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

public class SolicitarSubidaComprobanteService implements SolicitarSubidaComprobanteUseCase {

    private final AlmacenComprobantes almacen;
    private final EstadoDeCuentaRepository cuentas;
    private final UnidadRepository unidades;
    private final PoliticaVisibilidad politica;

    public SolicitarSubidaComprobanteService(
            AlmacenComprobantes almacen,
            EstadoDeCuentaRepository cuentas,
            UnidadRepository unidades,
            PoliticaVisibilidad politica) {
        this.almacen = almacen;
        this.cuentas = cuentas;
        this.unidades = unidades;
        this.politica = politica;
    }

    @Override
    public SubidaComprobante solicitar(
            Solicitante solicitante, UUID unidadId, String idempotencyKey, String tipoContenido) {
        var llave = IdempotencyKey.de(idempotencyKey);
        Set<UUID> propias = solicitante.tieneRol(Rol.ADMIN) ? Set.of() : unidades.idsDeUnidadesDe(solicitante.sub());
        politica.verificarPuedeVerEstadoDeCuenta(solicitante, unidadId, propias);
        var cuenta = cuentas.porUnidad(unidadId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la unidad " + unidadId));
        String clave = ClaveComprobante.para(cuenta.codigo(), llave, tipoContenido);
        var enlace = almacen.enlaceParaSubir(clave, tipoContenido);
        return new SubidaComprobante(clave, enlace.url(), enlace.expiraEn().truncatedTo(ChronoUnit.SECONDS).toString());
    }
}
