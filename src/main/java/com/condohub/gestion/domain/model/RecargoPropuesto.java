package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recargo por mora propuesto sobre un cargo vencido (F5). Nunca se cobra solo: el ADMIN lo confirma (se crea el cargo
 * RECARGO) o lo rechaza, y solo mientras está {@link EstadoRecargo#PROPUESTO}.
 */
public record RecargoPropuesto(
        UUID id,
        UUID unidadId,
        UUID cargoOrigenId,
        Periodo periodo,
        BigDecimal porcentaje,
        Dinero base,
        Dinero monto,
        EstadoRecargo estado,
        String origen,
        String motivo,
        UUID cargoGeneradoId) {

    public static final int MOTIVO_LARGO_MAXIMO = 300;

    /**
     * F5: solo un recargo propuesto se confirma. El cargo que se crea vence como una expensa: el día de vencimiento
     * (F4) del mes siguiente a la confirmación.
     *
     * @param diaVencimiento parámetro {@code expensas.dia_vencimiento}
     * @return fecha de vencimiento del cargo de recargo
     */
    public LocalDate confirmar(LocalDate hoy, int diaVencimiento) {
        exigirPropuesto();
        return Periodo.delMes(hoy).siguiente().fechaVencimiento(diaVencimiento);
    }

    /**
     * F5: como {@link #confirmar(LocalDate, int)}, y además exige que el cargo de origen siga debiendo lo mismo que al
     * proponerlo: el recargo se calculó sobre ese saldo vencido. Si desde entonces se pagó, total o en parte, ya no
     * corresponde: el ADMIN lo rechaza y el mes siguiente se propone sobre el saldo que quede.
     *
     * @param saldoActualDelOrigen saldo pendiente hoy del cargo de origen ({@link Dinero#CERO} si ya está pagado)
     */
    public LocalDate confirmar(LocalDate hoy, int diaVencimiento, Dinero saldoActualDelOrigen) {
        exigirPropuesto();
        if (saldoActualDelOrigen.equals(Dinero.CERO)) {
            throw new ReglaNegocioVioladaException(
                    "F5", "El cargo de origen ya fue pagado: no hay saldo vencido, rechaza este recargo");
        }
        if (!saldoActualDelOrigen.equals(base)) {
            throw new ReglaNegocioVioladaException(
                    "F5",
                    "El saldo del cargo de origen cambió desde la propuesta (era " + base + ", hoy " + saldoActualDelOrigen
                            + "): rechaza este recargo; el mes siguiente se propone sobre el saldo actual");
        }
        return confirmar(hoy, diaVencimiento);
    }

    /**
     * F5: el ADMIN rechaza el recargo; no se cobra.
     *
     * @return el motivo normalizado (puede ser {@code null})
     */
    public String rechazar(String motivoRechazo) {
        exigirPropuesto();
        return motivo(motivoRechazo);
    }

    /** Motivo opcional, sin espacios sobrantes y como máximo de {@value #MOTIVO_LARGO_MAXIMO} caracteres (F5). */
    public static String motivo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.length() > MOTIVO_LARGO_MAXIMO) {
            throw new ReglaNegocioVioladaException(
                    "F5", "El motivo admite como máximo " + MOTIVO_LARGO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    private void exigirPropuesto() {
        if (estado != EstadoRecargo.PROPUESTO) {
            throw new ReglaNegocioVioladaException("F5", "El recargo ya fue resuelto: está " + estado);
        }
    }
}
