package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.PagoRepository;
import com.condohub.gestion.domain.model.EstadoPago;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PagoPersistenceAdapter implements PagoRepository {

    private final PagoJpaRepository jpa;

    PagoPersistenceAdapter(PagoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Pago> pendientes() {
        return jpa.findByEstadoOrderByFechaPagoAsc(EstadoPago.PENDIENTE.name()).stream()
                .map(p -> new Pago(p.id, p.unidadId, Dinero.de(p.monto), p.fechaPago, EstadoPago.valueOf(p.estado)))
                .toList();
    }
}