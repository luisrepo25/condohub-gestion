package com.condohub.gestion.domain.valueobject;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Clave de la foto de un comprobante en el almacén de documentos (F8): una carpeta por unidad y, dentro, un archivo
 * con la {@code Idempotency-Key} del pago, así reintentar la subida pisa el mismo archivo y nunca se duplica.
 */
public final class ClaveComprobante {

    private static final String RAIZ = "comprobantes/";
    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");
    private static final Pattern ARCHIVO =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|pdf)");

    private ClaveComprobante() {}

    public static String para(String codigoUnidad, IdempotencyKey llave, String tipoContenido) {
        String extension = tipoContenido == null ? null : EXTENSIONES.get(tipoContenido.toLowerCase());
        if (extension == null) {
            throw new ReglaNegocioVioladaException(
                    "F8", "El comprobante debe ser una imagen (JPG, PNG, WebP) o un PDF: " + tipoContenido);
        }
        return RAIZ + codigoUnidad + "/" + llave.valor() + "." + extension;
    }

    /** Solo la carpeta de la propia unidad y con un nombre generado por el sistema: nada de rutas inventadas. */
    public static boolean perteneceA(String codigoUnidad, String clave) {
        String carpeta = RAIZ + codigoUnidad + "/";
        return clave != null
                && clave.startsWith(carpeta)
                && ARCHIVO.matcher(clave.substring(carpeta.length())).matches();
    }
}
