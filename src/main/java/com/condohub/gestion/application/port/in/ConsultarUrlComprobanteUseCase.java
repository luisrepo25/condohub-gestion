package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Solicitante;
import java.util.Optional;
import java.util.UUID;

public interface ConsultarUrlComprobanteUseCase {

    /** Enlace temporal a la foto del comprobante; vacío si el pago se registró sin foto (F1, F8). */
    Optional<String> urlDe(Solicitante solicitante, UUID pagoId);
}
