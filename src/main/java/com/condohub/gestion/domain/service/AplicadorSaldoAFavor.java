package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.Imputacion;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.model.SaldoAFavor;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F7: aplica el saldo a favor de una unidad a sus cargos pendientes. Los pagos se consumen en el orden recibido (el más
 * antiguo primero) y cada uno se reparte FIFO entre los cargos con {@link ImputadorPagos}.
 */
public class AplicadorSaldoAFavor {

    private final ImputadorPagos imputador;

    public AplicadorSaldoAFavor(ImputadorPagos imputador) {
        this.imputador = imputador;
    }

    /**
     * @param saldos saldo a favor de cada pago, del más antiguo al más reciente
     * @return qué parte de qué pago se aplica a qué cargo
     */
    public List<ImputacionDePago> aplicar(List<SaldoAFavor> saldos, List<CargoPendiente> cargosPendientes) {
        List<CargoPendiente> pendientes = new ArrayList<>(cargosPendientes);
        List<ImputacionDePago> resultado = new ArrayList<>();
        for (SaldoAFavor saldo : saldos) {
            if (pendientes.isEmpty()) {
                break;
            }
            var imputaciones = imputador.imputar(saldo.disponible(), pendientes).imputaciones();
            imputaciones.forEach(i -> resultado.add(new ImputacionDePago(saldo.pagoId(), i.cargoId(), i.monto())));
            pendientes = descontar(pendientes, imputaciones);
        }
        return List.copyOf(resultado);
    }

    private static List<CargoPendiente> descontar(List<CargoPendiente> cargos, List<Imputacion> imputaciones) {
        Map<UUID, BigDecimal> aplicado = new HashMap<>();
        imputaciones.forEach(i -> aplicado.merge(i.cargoId(), i.monto().monto(), BigDecimal::add));
        List<CargoPendiente> quedan = new ArrayList<>();
        for (CargoPendiente cargo : cargos) {
            BigDecimal saldo = cargo.saldo().monto().subtract(aplicado.getOrDefault(cargo.id(), BigDecimal.ZERO));
            if (saldo.signum() > 0) {
                quedan.add(new CargoPendiente(cargo.id(), Dinero.de(saldo), cargo.fechaVencimiento(), cargo.creadoEn()));
            }
        }
        return quedan;
    }
}
