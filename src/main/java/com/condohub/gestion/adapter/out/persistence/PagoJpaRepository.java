package com.condohub.gestion.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PagoJpaRepository extends JpaRepository<PagoJpa, UUID> {

    /** {@code estado} es un enum nativo de PostgreSQL ({@code estado_pago}); de ahí el CAST. */
    @Query(nativeQuery = true, value = "SELECT * FROM pago WHERE estado = CAST(:estado AS estado_pago) ORDER BY fecha_pago ASC")
    List<PagoJpa> findByEstadoOrderByFechaPagoAsc(@Param("estado") String estado);
}
