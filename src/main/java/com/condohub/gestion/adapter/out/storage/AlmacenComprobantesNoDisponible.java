package com.condohub.gestion.adapter.out.storage;

import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import com.condohub.gestion.domain.exception.AlmacenNoDisponibleException;

/** Se usa cuando el S3 no está configurado: el sistema sigue funcionando y los pagos quedan sin foto. */
public class AlmacenComprobantesNoDisponible implements AlmacenComprobantes {

    private static final String MENSAJE = "El almacén de comprobantes no está configurado";

    @Override
    public EnlaceTemporal enlaceParaSubir(String clave, String tipoContenido) {
        throw new AlmacenNoDisponibleException(MENSAJE);
    }

    @Override
    public EnlaceTemporal enlaceParaVer(String clave) {
        throw new AlmacenNoDisponibleException(MENSAJE);
    }
}
