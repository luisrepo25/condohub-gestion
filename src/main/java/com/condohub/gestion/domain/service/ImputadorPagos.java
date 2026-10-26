package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.Imputacion;
import com.condohub.gestion.domain.model.ResultadoImputacion;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * F7: reparte un pago confirmado entre los cargos pendientes de la unidad en orden FIFO: del vencimiento más antiguo al
 * más reciente (así los vencidos van primero) y, a igual vencimiento, el cargo creado antes. Lo que sobra queda como
 * saldo a favor.
 */
public class ImputadorPagos {

    private static final Comparator<CargoPendiente> FIFO =
            Comparator.comparing(CargoPendiente::fechaVencimiento).thenComparing(CargoPendiente::creadoEn);

    public ResultadoImputacion imputar(Dinero monto, Collection<CargoPendiente> cargosPendientes) {
        BigDecimal restante = monto.monto();
        List<Imputacion> imputaciones = new ArrayList<>();
        for (CargoPendiente cargo : cargosPendientes.stream().sorted(FIFO).toList()) {
            if (restante.signum() == 0) {
                break;
            }
            BigDecimal aplicado = restante.min(cargo.saldo().monto());
            if (aplicado.signum() > 0) {
                imputaciones.add(new Imputacion(cargo.id(), Dinero.de(aplicado)));
                restante = restante.subtract(aplicado);
            }
        }
        return new ResultadoImputacion(imputaciones, Dinero.de(restante));
    }
}
