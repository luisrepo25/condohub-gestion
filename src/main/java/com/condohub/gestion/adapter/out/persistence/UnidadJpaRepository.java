package com.condohub.gestion.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UnidadJpaRepository extends JpaRepository<UnidadJpa, UUID> {

    @Query(nativeQuery = true, value = """
            SELECT u.id FROM unidad u
              JOIN persona p ON p.id = u.propietario_titular_id
             WHERE p.keycloak_sub = :sub
            UNION
            SELECT ru.unidad_id FROM residente_unidad ru
              JOIN persona p ON p.id = ru.persona_id
             WHERE p.keycloak_sub = :sub
               AND ru.desde <= current_date
               AND (ru.hasta IS NULL OR ru.hasta >= current_date)
            """)
    List<UUID> idsPorKeycloakSub(@Param("sub") String sub);
}