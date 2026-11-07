package com.condohub.gestion.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Enlace entre un pago y la foto de su comprobante (F8). */
public interface ComprobantePagoRepository {

    /** Unidad del pago y su foto (la clave es nula si no tiene); vacío si el pago no existe. */
    Optional<ComprobanteDePago> porPago(UUID pagoId);

    /** Guarda la clave de la foto si el pago todavía no tiene una. */
    void enlazar(UUID pagoId, String clave);

    record ComprobanteDePago(UUID unidadId, String clave) {}
}
