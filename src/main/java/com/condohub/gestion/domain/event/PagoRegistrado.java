package com.condohub.gestion.domain.event;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.LocalDate;
import java.util.UUID;

/** Un residente registró un pago; nace PENDIENTE (F8, F9). n8n lo compara con el extracto. */
public record PagoRegistrado(UUID pagoId, UUID unidadId, Dinero monto, LocalDate fechaPago, String banco)
        implements EventoFinanzas {

    @Override
    public String tipo() {
        return "finanzas.PagoRegistrado";
    }

    @Override
    public UUID agregadoId() {
        return pagoId;
    }
}
