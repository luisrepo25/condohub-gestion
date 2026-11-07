package com.condohub.gestion.adapter.out.storage;

import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import java.time.Clock;
import java.time.Instant;

/** Fotos de los comprobantes en el bucket de documentos (S3 real de AWS), con enlaces prefirmados y temporales. */
public class S3AlmacenComprobantes implements AlmacenComprobantes {

    private final S3UrlPrefirmada firmador;
    private final String bucket;
    private final String host;
    private final int vigenciaSegundos;
    private final Clock reloj;

    public S3AlmacenComprobantes(S3UrlPrefirmada firmador, String bucket, String region, int vigenciaSegundos, Clock reloj) {
        this.firmador = firmador;
        this.bucket = bucket;
        this.host = "s3." + region + ".amazonaws.com";
        this.vigenciaSegundos = vigenciaSegundos;
        this.reloj = reloj;
    }

    @Override
    public EnlaceTemporal enlaceParaSubir(String clave, String tipoContenido) {
        return enlace("PUT", clave);
    }

    @Override
    public EnlaceTemporal enlaceParaVer(String clave) {
        return enlace("GET", clave);
    }

    private EnlaceTemporal enlace(String metodo, String clave) {
        Instant ahora = reloj.instant();
        String url = firmador.firmar(metodo, host, "/" + bucket + "/" + clave, ahora, vigenciaSegundos);
        return new EnlaceTemporal(url, ahora.plusSeconds(vigenciaSegundos));
    }
}
