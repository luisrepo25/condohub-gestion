package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.out.SaldoAFavorRepository;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.service.AplicadorSaldoAFavor;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
}
