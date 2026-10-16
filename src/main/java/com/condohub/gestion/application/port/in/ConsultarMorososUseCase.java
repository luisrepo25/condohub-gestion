package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Moroso;
import java.util.List;

public interface ConsultarMorososUseCase {

    /** Unidades morosas, de mayor a menor deuda vencida. */
    List<Moroso> consultar();
}