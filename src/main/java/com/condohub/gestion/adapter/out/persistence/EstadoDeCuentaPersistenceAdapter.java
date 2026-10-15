package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.domain.model.EstadoDeCuenta;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EstadoDeCuentaPersistenceAdapter implements EstadoDeCuentaRepository {

    private final EstadoCuentaJpaRepository jpa;

    EstadoDeCuentaPersistenceAdapter(EstadoCuentaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<EstadoDeCuenta> porUnidad(UUID unidadId) {
        return jpa.findById(unidadId).map(e -> new EstadoDeCuenta(
                e.unidadId,
                e.codigo,
                e.bloque,
                Dinero.de(e.deudaTotal),
                Dinero.de(e.deudaVencida),
                e.expensasVencidas,
                Dinero.de(e.saldoAFavor),
                e.moroso));
    }
}