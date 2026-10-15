package com.condohub.gestion.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "pago")
class PagoJpa {

    @Id
    UUID id;

    @Column(name = "unidad_id")
    UUID unidadId;

    BigDecimal monto;

    @Column(name = "fecha_pago")
    LocalDate fechaPago;

    String estado;

    protected PagoJpa() {}
}