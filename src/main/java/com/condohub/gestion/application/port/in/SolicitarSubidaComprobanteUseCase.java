package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Solicitante;
import java.util.UUID;

public interface SolicitarSubidaComprobanteUseCase {

    /** Entrega el enlace temporal para subir la foto a la carpeta de la unidad (F1, F8). */
    SubidaComprobante solicitar(Solicitante solicitante, UUID unidadId, String idempotencyKey, String tipoContenido);

    record SubidaComprobante(String clave, String url, String expiraEn) {}
}
