package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import java.util.Set;
import java.util.UUID;

/**
 * F1: solo el titular y los residentes de una unidad ven su estado de cuenta.
 * El {@code ADMIN} administra el condominio y ve todas las unidades.
 */
public class PoliticaVisibilidad {

    /**
     * @param unidadesDelSolicitante unidades de las que el solicitante es titular o residente vigente
     * @throws ReglaNegocioVioladaException {@code F1} si no puede ver la unidad
     */
    public void verificarPuedeVerEstadoDeCuenta(
            Solicitante solicitante, UUID unidadId, Set<UUID> unidadesDelSolicitante) {
        if (solicitante.tieneRol(Rol.ADMIN)) {
            return;
        }
        if (solicitante.tieneRol(Rol.RESIDENTE) && unidadesDelSolicitante.contains(unidadId)) {
            return;
        }
        throw new ReglaNegocioVioladaException("F1", "Solo el titular y los residentes ven el estado de cuenta de su unidad");
    }
}
