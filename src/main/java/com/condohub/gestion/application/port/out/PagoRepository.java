package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.Pago;
import java.util.List;

public interface PagoRepository {

    List<Pago> pendientes();
}
