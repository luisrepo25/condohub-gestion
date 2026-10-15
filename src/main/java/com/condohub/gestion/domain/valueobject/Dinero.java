package com.condohub.gestion.domain.valueobject;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Monto de dinero: {@link BigDecimal} con exactamente 2 decimales y nunca negativo (F10). */
public record Dinero(BigDecimal monto) {

    public static final Dinero CERO = new Dinero(BigDecimal.ZERO);

    public Dinero {
        Objects.requireNonNull(monto, "monto");
        try {
            monto = monto.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new ReglaNegocioVioladaException("F10", "El dinero admite como máximo 2 decimales: " + monto);
        }
        if (monto.signum() < 0) {
            throw new ReglaNegocioVioladaException("F10", "El dinero no puede ser negativo: " + monto);
        }
    }

    public static Dinero de(String valor) {
        return new Dinero(new BigDecimal(valor));
    }

    public static Dinero de(BigDecimal valor) {
        return new Dinero(valor);
    }

    @Override
    public String toString() {
        return monto.toPlainString();
    }
}
