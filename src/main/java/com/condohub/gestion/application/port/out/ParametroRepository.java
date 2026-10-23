package com.condohub.gestion.application.port.out;

/** Parámetros de negocio de la tabla {@code parametro}: nunca hardcodeados en el código. */
public interface ParametroRepository {

    /** Valor entero del parámetro; falla si no existe. */
    int entero(String clave);
}
