package com.condohub.gestion.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EstadoCuentaJpaRepository extends JpaRepository<EstadoCuentaJpa, UUID> {}