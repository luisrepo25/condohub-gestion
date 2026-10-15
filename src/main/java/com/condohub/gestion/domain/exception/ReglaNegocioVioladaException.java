package com.condohub.gestion.domain.exception;

/** Una regla de negocio de docs/negocio/reglas-de-negocio.md impide la operación. */
public class ReglaNegocioVioladaException extends RuntimeException {

    private final String codigo;

    public ReglaNegocioVioladaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    /** Código de la regla violada, ej. {@code F1}. */
    public String codigo() {
        return codigo;
    }
}
