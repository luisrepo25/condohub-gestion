package com.condohub.gestion.domain.exception;

/** El almacén de documentos (S3) no está configurado o no responde: el pago puede registrarse igual, sin foto. */
public class AlmacenNoDisponibleException extends RuntimeException {

    public AlmacenNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
