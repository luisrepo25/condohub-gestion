package com.condohub.gestion.application.port.out;

import java.time.Instant;

/** Almacén de documentos donde viven las fotos de los comprobantes (F8). Solo entrega enlaces temporales. */
public interface AlmacenComprobantes {

    /** Enlace para que el cliente suba la foto directo al almacén. */
    EnlaceTemporal enlaceParaSubir(String clave, String tipoContenido);

    /** Enlace temporal para ver la foto. */
    EnlaceTemporal enlaceParaVer(String clave);

    record EnlaceTemporal(String url, Instant expiraEn) {}
}
