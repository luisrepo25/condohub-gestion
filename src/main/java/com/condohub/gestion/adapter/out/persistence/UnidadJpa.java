package com.condohub.gestion.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "unidad")
class UnidadJpa {

    @Id
    UUID id;

    protected UnidadJpa() {}
}