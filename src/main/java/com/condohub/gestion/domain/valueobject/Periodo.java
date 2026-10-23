package com.condohub.gestion.domain.valueobject;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** Mes de emisión de expensas (año-mes, ej. {@code 2026-11}). Las expensas se emiten el día 1 (F3) y vencen en el mismo mes (F4). */
public record Periodo(YearMonth mes) {

    public Periodo {
        Objects.requireNonNull(mes, "mes");
    }

    /** @param valor año-mes en formato ISO {@code yyyy-MM} */
    public static Periodo de(String valor) {
        try {
            return new Periodo(YearMonth.parse(valor));
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioVioladaException("F3", "El periodo debe tener el formato año-mes (yyyy-MM): " + valor);
        }
    }

    /** Periodo al que pertenece una fecha. */
    public static Periodo delMes(LocalDate fecha) {
        return new Periodo(YearMonth.from(fecha));
    }

    public boolean esAnteriorA(Periodo otro) {
        return mes.isBefore(otro.mes);
    }

    /** F3: primer día del mes, en que se emiten las expensas. */
    public LocalDate primerDia() {
        return mes.atDay(1);
    }

    /**
     * F4: fecha de vencimiento de las expensas del periodo.
     *
     * @param diaVencimiento parámetro {@code expensas.dia_vencimiento} (10 por defecto); posterior al día de emisión
     */
    public LocalDate fechaVencimiento(int diaVencimiento) {
        if (diaVencimiento < 2 || diaVencimiento > mes.lengthOfMonth()) {
            throw new ReglaNegocioVioladaException(
                    "F4", "El día de vencimiento " + diaVencimiento + " no es válido para " + this);
        }
        return mes.atDay(diaVencimiento);
    }

    @Override
    public String toString() {
        return mes.toString();
    }
}
