package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Alicuota;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F3: expensa de cada unidad = presupuesto mensual × alícuota, redondeada a 2 decimales. La diferencia de redondeo va a
 * la unidad de mayor alícuota, así las expensas suman exactamente el presupuesto. Exige antes que las alícuotas sumen
 * 100 % (F2).
 */
public class CalculadoraExpensas {

    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * @param alicuotasPorUnidad alícuota de cada unidad activa, por id de unidad
     * @return expensa de cada unidad, en el mismo orden que {@code alicuotasPorUnidad}
     * @throws ReglaNegocioVioladaException {@code F2} si las alícuotas no suman 100 %; {@code F3} si el presupuesto es 0
     */
    public Map<UUID, Dinero> calcular(Dinero presupuesto, Map<UUID, Alicuota> alicuotasPorUnidad) {
        if (presupuesto.monto().signum() == 0) {
            throw new ReglaNegocioVioladaException("F3", "El presupuesto mensual debe ser mayor a 0");
        }
        Alicuota.exigirQueSumenCien(alicuotasPorUnidad.values());

        Map<UUID, BigDecimal> montos = new LinkedHashMap<>();
        alicuotasPorUnidad.forEach((unidadId, alicuota) -> montos.put(
                unidadId,
                presupuesto.monto().multiply(alicuota.porcentaje()).divide(CIEN, 2, RoundingMode.HALF_UP)));

        BigDecimal suma = montos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diferencia = presupuesto.monto().subtract(suma);
        if (diferencia.signum() != 0) {
            // TODO(regla): F3 no dice qué unidad absorbe el redondeo si varias empatan en la mayor alícuota;
            // hoy se elige la primera en el orden recibido.
            UUID mayor = alicuotasPorUnidad.entrySet().stream()
                    .max(Comparator.comparing((Map.Entry<UUID, Alicuota> e) -> e.getValue().porcentaje()))
                    .map(Map.Entry::getKey)
                    .orElseThrow();
            montos.merge(mayor, diferencia, BigDecimal::add);
        }

        Map<UUID, Dinero> expensas = new LinkedHashMap<>();
        montos.forEach((unidadId, monto) -> expensas.put(unidadId, Dinero.de(monto)));
        return Collections.unmodifiableMap(expensas);
    }
}
