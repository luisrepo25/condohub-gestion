package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.adapter.out.storage.S3UrlPrefirmada;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** El firmador SigV4 se valida con el ejemplo publicado en la documentación de AWS (GET prefirmado, 24 h). */
class S3UrlPrefirmadaTest {

    @Test
    void la_firma_coincide_con_el_ejemplo_de_la_documentacion_de_AWS() {
        var firmador = new S3UrlPrefirmada("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", "us-east-1");

        String url = firmador.firmar(
                "GET", "examplebucket.s3.amazonaws.com", "/test.txt", Instant.parse("2013-05-24T00:00:00Z"), 86400);

        assertThat(url)
                .isEqualTo("https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                        + "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request"
                        + "&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host"
                        + "&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404");
    }

    @Test
    void la_clave_con_barras_se_codifica_por_segmento() {
        var firmador = new S3UrlPrefirmada("AK", "SK", "us-east-1");

        String url = firmador.firmar(
                "PUT", "s3.us-east-1.amazonaws.com", "/bucket/comprobantes/A-101/a b.jpg",
                Instant.parse("2026-11-07T12:00:00Z"), 900);

        assertThat(url).startsWith("https://s3.us-east-1.amazonaws.com/bucket/comprobantes/A-101/a%20b.jpg?");
    }
}
