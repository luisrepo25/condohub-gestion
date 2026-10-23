package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import com.condohub.gestion.domain.valueobject.Alicuota;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Agregado {@code PeriodoExpensas}: el presupuesto de un mes y las expensas emitidas a cada unidad activa (F3), con su
 * vencimiento (F4). Se crea ya emitido con {@link #emitir}; un periodo se emite una sola vez.
 */
public record PeriodoExpensas(
        Periodo periodo, Dinero presupuesto, LocalDate fechaVencimiento, Map<UUID, Dinero> expensas, String emitidoPor) {

    public PeriodoExpensas {
        expensas = Map.copyOf(expensas);
    }

    /**
     * F2, F3, F4: calcula y emite las expensas del periodo. Solo el mes actual o uno futuro (F3).
     *
     * @param periodoActual mes en curso según el reloj del sistema
     * @param alicuotasPorUnidad alícuotas de las unidades activas, por id de unidad
     * @param diaVencimiento parámetro {@code expensas.dia_vencimiento}
     * @param emitidoPor {@code sub} de Keycloak del ADMIN que emite
     */
    public static PeriodoExpensas emitir(
            Periodo periodo,
            Periodo periodoActual,
            Dinero presupuesto,
            Map<UUID, Alicuota> alicuotasPorUnidad,
            int diaVencimiento,
            String emitidoPor,
            CalculadoraExpensas calculadora) {
        if (periodo.esAnteriorA(periodoActual)) {
            throw new ReglaNegocioVioladaException(
                    "F3", "No se emiten expensas de un mes pasado (" + periodo + "): nacerían vencidas");
        }
        var expensas = calculadora.calcular(presupuesto, alicuotasPorUnidad);
        if (expensas.values().stream().anyMatch(expensa -> expensa.monto().signum() == 0)) {
            throw new ReglaNegocioVioladaException(
                    "F3", "El presupuesto es tan bajo que alguna expensa quedaría en 0; un cargo debe ser mayor a 0");
        }
        return new PeriodoExpensas(periodo, presupuesto, periodo.fechaVencimiento(diaVencimiento), expensas, emitidoPor);
    }

    /** Suma de las expensas emitidas; igual al presupuesto (F3). */
    public Dinero total() {
        return Dinero.de(expensas.values().stream().map(Dinero::monto).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
