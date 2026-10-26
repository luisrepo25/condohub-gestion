package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.RecargoPropuesto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProponerRecargoUseCase {

    /** @param monto total esperado por quien propone; opcional */
    record Comando(UUID unidadId, BigDecimal monto, String motivo, String misionId) {}

    /** F5: propone (sin cobrar) los recargos del mes de la unidad; devuelve las propuestas del mes. */
    List<RecargoPropuesto> proponer(Comando comando);
}
