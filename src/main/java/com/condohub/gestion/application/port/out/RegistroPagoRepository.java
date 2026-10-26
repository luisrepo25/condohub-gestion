package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.RegistroDePago;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;

public interface RegistroPagoRepository {

    /** Pago ya registrado con esa clave, si existe (O3). */
    Optional<Pago> porIdempotencyKey(IdempotencyKey clave);

    boolean existeUnidad(UUID unidadId);

    /**
     * Guarda el pago. Si otra petición con la misma clave se adelantó, no crea otro y devuelve el ya guardado (O3,
     * respaldado por {@code pago.idempotency_key UNIQUE}).
     */
    Pago guardar(RegistroDePago registro);
}
