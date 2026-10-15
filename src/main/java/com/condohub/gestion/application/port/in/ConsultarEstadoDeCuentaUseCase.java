package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.EstadoDeCuenta;
import com.condohub.gestion.domain.model.Solicitante;
import java.util.UUID;

public interface ConsultarEstadoDeCuentaUseCase {

    EstadoDeCuenta consultar(Solicitante solicitante, UUID unidadId);
}
