package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.CargoVencido;
import com.condohub.gestion.domain.model.RecargoCalculado;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * F5: recargo por mora = porcentaje mensual simple × saldo vencido de cada cargo, redondeado a 2 decimales. Simple: la
 * base es el saldo del cargo, nunca un recargo anterior. Solo calcula; aplicarlo exige la confirmación del ADMIN.
 */
public class CalculadoraRecargos {

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * @param porcentajeMensual parámetro {@code recargo.porcentaje_mensual} (2.00 inicial)
     * @return un recargo por cargo vencido; los que redondean a 0 no se proponen
     */
    public List<RecargoCalculado> calcular(BigDecimal porcentajeMensual, Collection<CargoVencido> vencidos) {
        if (porcentajeMensual.signum() <= 0) {
            throw new ReglaNegocioVioladaException("F5", "El porcentaje de recargo debe ser mayor a 0");
        }
        return vencidos.stream()
                .map(cargo -> new RecargoCalculado(
                        cargo.id(),
                        cargo.saldo(),
                        Dinero.de(cargo.saldo().monto().multiply(porcentajeMensual).divide(CIEN, 2, RoundingMode.HALF_UP))))
                .filter(recargo -> recargo.monto().monto().signum() > 0)
                .toList();
    }

    /** F5: si quien propone (p. ej. el agente) envía un total, debe coincidir con el que calcula Gestión. */
    public void exigirMontoSolicitado(Dinero solicitado, Dinero calculado) {
        if (!solicitado.equals(calculado)) {
            throw new ReglaNegocioVioladaException(
                    "F5", "El recargo del mes es " + calculado + " (porcentaje mensual sobre el saldo vencido); se pidió "
                            + solicitado);
        }
    }
}
