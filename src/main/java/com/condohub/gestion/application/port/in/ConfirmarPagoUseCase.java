package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.UUID;

public interface ConfirmarPagoUseCase {

    /** Resultado de confirmar un pago: el pago, su recibo y cómo se imputó (F7, F8). */
    record PagoConfirmado(Pago pago, long reciboNumero, Dinero imputado, Dinero saldoAFavor) {}

    /** F7, F8: confirma un pago PENDIENTE, lo imputa FIFO y emite el recibo. */
    PagoConfirmado confirmar(String adminSub, UUID pagoId);
}
