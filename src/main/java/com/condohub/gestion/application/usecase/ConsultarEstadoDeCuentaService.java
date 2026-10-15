package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ConsultarEstadoDeCuentaUseCase;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.model.EstadoDeCuenta;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ConsultarEstadoDeCuentaService implements ConsultarEstadoDeCuentaUseCase {

    private final EstadoDeCuentaRepository estados;
    private final UnidadRepository unidades;
    private final PoliticaVisibilidad politica;

    public ConsultarEstadoDeCuentaService(
            EstadoDeCuentaRepository estados, UnidadRepository unidades, PoliticaVisibilidad politica) {
        this.estados = estados;
        this.unidades = unidades;
        this.politica = politica;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoDeCuenta consultar(Solicitante solicitante, UUID unidadId) {
        // F1 primero: quien no puede ver la unidad no averigua si existe.
        Set<UUID> propias = solicitante.tieneRol(Rol.ADMIN) ? Set.of() : unidades.idsDeUnidadesDe(solicitante.sub());
        politica.verificarPuedeVerEstadoDeCuenta(solicitante, unidadId, propias);
        return estados.porUnidad(unidadId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la unidad " + unidadId));
    }
}
