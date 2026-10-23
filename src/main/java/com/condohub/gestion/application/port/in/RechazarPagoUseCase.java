package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Pago;
import java.util.UUID;

public interface RechazarPagoUseCase {

    /** Resultado de rechazar un pago, con el motivo guardado (F8). */
    record PagoRechazado(Pago pago, String motivo) {}

    /** F8: rechaza un pago PENDIENTE con motivo obligatorio; no toca ningún cargo. */
    PagoRechazado rechazar(String adminSub, UUID pagoId, String motivo);
}
