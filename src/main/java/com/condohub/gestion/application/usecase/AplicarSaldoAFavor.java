package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.out.SaldoAFavorRepository;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.service.AplicadorSaldoAFavor;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * F7: paso que usan los casos de uso que crean cargos: aplica el saldo a favor de cada unidad a sus cargos pendientes.
 * Corre dentro de la transacción del caso de uso que lo llama.
 */
public class AplicarSaldoAFavor {

    private final SaldoAFavorRepository saldos;
    private final AplicadorSaldoAFavor aplicador;

    public AplicarSaldoAFavor(SaldoAFavorRepository saldos, AplicadorSaldoAFavor aplicador) {
        this.saldos = saldos;
        this.aplicador = aplicador;
    }

    /** @return total de saldo a favor aplicado */
    public Dinero aplicar() {
        List<ImputacionDePago> todas = new ArrayList<>();
        saldos.saldosAFavorPorUnidad().forEach((unidadId, deLaUnidad) ->
                todas.addAll(aplicador.aplicar(deLaUnidad, saldos.cargosPendientes(unidadId))));
        if (!todas.isEmpty()) {
            saldos.imputar(todas);
        }
        return Dinero.de(todas.stream().map(i -> i.monto().monto()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * Como {@link #aplicar()}, pero solo para una unidad: lo usan los casos de uso que crean un cargo de una sola
     * unidad (un recargo), así no se leen ni bloquean los pagos de las demás.
     *
     * @return total de saldo a favor aplicado
     */
    public Dinero aplicar(UUID unidadId) {
        var deLaUnidad = saldos.saldosAFavorDe(unidadId);
        if (deLaUnidad.isEmpty()) {
            return Dinero.CERO;
        }
        List<ImputacionDePago> imputaciones = aplicador.aplicar(deLaUnidad, saldos.cargosPendientes(unidadId));
        if (!imputaciones.isEmpty()) {
            saldos.imputar(imputaciones);
        }
        return Dinero.de(imputaciones.stream().map(i -> i.monto().monto()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
