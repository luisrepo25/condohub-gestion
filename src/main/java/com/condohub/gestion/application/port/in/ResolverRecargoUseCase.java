package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.RecargoPropuesto;
import java.util.List;
import java.util.UUID;

/** F5: el ADMIN revisa los recargos propuestos; solo al confirmar se cobra. */
public interface ResolverRecargoUseCase {

    List<RecargoPropuesto> pendientes();

    /** Crea el cargo RECARGO y aplica el saldo a favor de la unidad (F7). */
    RecargoPropuesto confirmar(String adminSub, UUID propuestaId);

    RecargoPropuesto rechazar(String adminSub, UUID propuestaId, String motivo);
}
