package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import com.condohub.gestion.application.port.out.ComprobantePagoRepository;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.valueobject.ClaveComprobante;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra el pago con el caso de uso de siempre y, si el cliente subió la foto del comprobante, la enlaza (F8).
 * Sin foto (p. ej. sin conexión) el pago se registra igual. Un pago nunca se confirma desde el cliente (F9).
 */
public class RegistrarPagoConComprobanteService implements RegistrarPagoUseCase {

    private final RegistrarPagoUseCase base;
    private final ComprobantePagoRepository comprobantes;
    private final EstadoDeCuentaRepository cuentas;

    public RegistrarPagoConComprobanteService(
            RegistrarPagoUseCase base, ComprobantePagoRepository comprobantes, EstadoDeCuentaRepository cuentas) {
        this.base = base;
        this.comprobantes = comprobantes;
        this.cuentas = cuentas;
    }

    @Override
    @Transactional
    public Pago registrar(Solicitante solicitante, Comando comando) {
        var pago = base.registrar(solicitante, comando);
        String clave = comando.comprobanteKey();
        if (clave != null && !clave.isBlank()) {
            exigirDeLaUnidad(pago, clave);
            comprobantes.enlazar(pago.id(), clave);
        }
        return pago;
    }

    private void exigirDeLaUnidad(Pago pago, String clave) {
        var codigo = cuentas.porUnidad(pago.unidadId()).map(c -> c.codigo()).orElse("");
        if (!ClaveComprobante.perteneceA(codigo, clave)) {
            throw new ReglaNegocioVioladaException("F8", "La foto del comprobante no es de la carpeta de esta unidad");
        }
    }
}
