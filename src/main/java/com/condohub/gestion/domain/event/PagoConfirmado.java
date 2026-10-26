package com.condohub.gestion.domain.event;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

/** El ADMIN confirmó el pago y se emitió el recibo (F8). n8n envía el recibo al residente. */
public record PagoConfirmado(UUID pagoId, UUID unidadId, Dinero monto, long reciboNumero) implements EventoFinanzas {

    @Override
    public String tipo() {
        return "finanzas.PagoConfirmado";
    }

    @Override
    public UUID agregadoId() {
        return pagoId;
    }

    @Override
    public String tipoAgregado() {
        return "Pago";
    }
}
