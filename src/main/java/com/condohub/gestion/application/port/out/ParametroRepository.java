package com.condohub.gestion.application.port.out;

import java.math.BigDecimal;

/** Parámetros de negocio de la tabla {@code parametro}: nunca hardcodeados en el código. */
public interface ParametroRepository {

    /** Valor entero del parámetro; falla si no existe. */
    int entero(String clave);

    /** Valor decimal del parámetro (ej. un porcentaje); falla si no existe. */
    BigDecimal decimal(String clave);
}
