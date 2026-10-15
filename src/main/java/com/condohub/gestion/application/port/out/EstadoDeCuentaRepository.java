package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.EstadoDeCuenta;
import java.util.Optional;
import java.util.UUID;

public interface EstadoDeCuentaRepository {

    Optional<EstadoDeCuenta> porUnidad(UUID unidadId);
}
