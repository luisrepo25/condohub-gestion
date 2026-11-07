package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SimularPagoUseCase {

    /**
     * Muestra a qué cargos se aplicaría un pago de ese monto con la misma regla FIFO de la confirmación (F7). No guarda
     * nada (F9). El RESIDENTE solo para su unidad (F1).
     */
    Simulacion simular(Solicitante solicitante, UUID unidadId, BigDecimal monto);

    /** Un cargo al que llegaría el pago: su saldo antes, lo que se aplicaría y su saldo después. */
    record Linea(
            UUID cargoId,
            String tipo,
            String concepto,
            LocalDate fechaVencimiento,
            Dinero saldoActual,
            Dinero aplicado,
            Dinero saldoResultante) {}

    /** Las líneas en el orden de aplicación y lo que sobraría como saldo a favor. */
    record Simulacion(List<Linea> lineas, Dinero excedente) {}
}
