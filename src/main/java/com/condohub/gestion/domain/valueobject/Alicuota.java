package com.condohub.gestion.domain.valueobject;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Porcentaje de participación de una unidad en el presupuesto mensual: mayor a 0, como máximo 100 y con 4 decimales
 * (columna {@code unidad.alicuota NUMERIC(7,4)}). Las alícuotas de las unidades activas deben sumar 100 % (F2).
 */
public record Alicuota(BigDecimal porcentaje) {

    private static final BigDecimal CIEN = new BigDecimal("100");

    public Alicuota {
        Objects.requireNonNull(porcentaje, "porcentaje");
        try {
            porcentaje = porcentaje.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ReglaNegocioVioladaException("F2", "La alícuota admite como máximo 4 decimales: " + porcentaje);
        }
        if (porcentaje.signum() <= 0 || porcentaje.compareTo(CIEN) > 0) {
            throw new ReglaNegocioVioladaException("F2", "La alícuota debe ser mayor a 0 y como máximo 100: " + porcentaje);
        }
    }

    public static Alicuota de(String valor) {
        return new Alicuota(new BigDecimal(valor));
    }

    public static Alicuota de(BigDecimal valor) {
        return new Alicuota(valor);
    }

    /** F2: las alícuotas de las unidades activas suman exactamente 100 %. */
    public static boolean sumanCien(Collection<Alicuota> alicuotas) {
        return alicuotas.stream().map(Alicuota::porcentaje).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(CIEN) == 0;
    }

    /** F2: no se emiten expensas si las alícuotas no cuadran. */
    public static void exigirQueSumenCien(Collection<Alicuota> alicuotas) {
        if (!sumanCien(alicuotas)) {
            BigDecimal suma = alicuotas.stream().map(Alicuota::porcentaje).reduce(BigDecimal.ZERO, BigDecimal::add);
            throw new ReglaNegocioVioladaException(
                    "F2", "Las alícuotas de las unidades activas suman " + suma.toPlainString() + " % y deben sumar 100 %");
        }
    }

    @Override
    public String toString() {
        return porcentaje.toPlainString();
    }
}
