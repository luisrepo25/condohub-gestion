package com.condohub.gestion.adapter.out.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** URL prefirmada de S3 con AWS Signature V4 (solo el encabezado host se firma). Sin SDK: solo la plataforma. */
public class S3UrlPrefirmada {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final String accessKey;
    private final String secretKey;
    private final String region;

    public S3UrlPrefirmada(String accessKey, String secretKey, String region) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
    }

    /** @param ruta ruta sin codificar y con barra inicial (estilo ruta: {@code /bucket/clave}) */
    public String firmar(String metodo, String host, String ruta, Instant ahora, int vigenciaSegundos) {
        String fecha = FECHA.format(ahora);
        String alcance = fecha + "/" + region + "/s3/aws4_request";
        String consulta = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + codificar(accessKey + "/" + alcance)
                + "&X-Amz-Date=" + FECHA_HORA.format(ahora)
                + "&X-Amz-Expires=" + vigenciaSegundos
                + "&X-Amz-SignedHeaders=host";
        String rutaCodificada = codificarRuta(ruta);
        String canonica = metodo + "\n" + rutaCodificada + "\n" + consulta + "\nhost:" + host + "\n\nhost\nUNSIGNED-PAYLOAD";
        String aFirmar = "AWS4-HMAC-SHA256\n" + FECHA_HORA.format(ahora) + "\n" + alcance + "\n" + sha256(canonica);
        byte[] clave = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), fecha);
        for (String parte : new String[] {region, "s3", "aws4_request"}) {
            clave = hmac(clave, parte);
        }
        String firma = HexFormat.of().formatHex(hmac(clave, aFirmar));
        return "https://" + host + rutaCodificada + "?" + consulta + "&X-Amz-Signature=" + firma;
    }

    private static String codificarRuta(String ruta) {
        String[] segmentos = ruta.split("/", -1);
        for (int i = 0; i < segmentos.length; i++) {
            segmentos[i] = codificar(segmentos[i]);
        }
        return String.join("/", segmentos);
    }

    /** Codificación RFC 3986: solo quedan sin codificar letras, dígitos y {@code -_.~}. */
    private static String codificar(String texto) {
        var salida = new StringBuilder();
        for (byte b : texto.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.~".indexOf(c) >= 0) {
                salida.append(c);
            } else {
                salida.append('%').append(String.format("%02X", b & 0xff));
            }
        }
        return salida.toString();
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] clave, String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
