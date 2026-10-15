package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.UnidadRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnidadPersistenceAdapter implements UnidadRepository {

    private final UnidadJpaRepository jpa;

    UnidadPersistenceAdapter(UnidadJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Set<UUID> idsDeUnidadesDe(String keycloakSub) {
        return new HashSet<>(jpa.idsPorKeycloakSub(keycloakSub));
    }
}