package com.condohub.gestion.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Fila de la vista {@code v_estado_cuenta}. Entidad JPA: vive solo en el adaptador, nunca en el dominio. */
@Entity
@Immutable
@Table(name = "v_estado_cuenta")
class EstadoCuentaJpa {

    @Id
    @Column(name = "unidad_id")
    UUID unidadId;

    String codigo;
    String bloque;

    @Column(name = "deuda_total")
    BigDecimal deudaTotal;

    @Column(name = "deuda_vencida")
    BigDecimal deudaVencida;

    @Column(name = "expensas_vencidas")
    long expensasVencidas;

    @Column(name = "saldo_a_favor")
    BigDecimal saldoAFavor;

    boolean moroso;

    protected EstadoCuentaJpa() {}
}