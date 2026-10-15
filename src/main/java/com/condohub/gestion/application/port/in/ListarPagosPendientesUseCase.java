package com.condohub.gestion.application.port.in;

import com.condohub.gestion.domain.model.Pago;
import java.util.List;

public interface ListarPagosPendientesUseCase {

    /** Pagos en estado PENDIENTE (F8), del más antiguo al más reciente. */
    List<Pago> listar();
}
