package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.application.usecase.ConsultarEstadoDeCuentaService;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.EstadoDeCuenta;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarEstadoDeCuentaServiceTest {

    private final UUID miUnidad = UUID.randomUUID();
    private final UUID otraUnidad = UUID.randomUUID();

    private final ConsultarEstadoDeCuentaService servicio = new ConsultarEstadoDeCuentaService(
            id -> Optional.of(new EstadoDeCuenta(id, "A-101", "A", Dinero.CERO, Dinero.CERO, 0, Dinero.CERO, false)),
            sub -> Set.of(miUnidad),
            new PoliticaVisibilidad());

    @Test
    void F1_residente_obtiene_el_estado_de_su_unidad() {
        var residente = new Solicitante("sub-res", Set.of(Rol.RESIDENTE));
        assertThat(servicio.consultar(residente, miUnidad).unidadId()).isEqualTo(miUnidad);
    }

    @Test
    void F1_residente_que_pide_otra_unidad_recibe_F1_sin_saber_si_existe() {
        var residente = new Solicitante("sub-res", Set.of(Rol.RESIDENTE));
        assertThatThrownBy(() -> servicio.consultar(residente, otraUnidad))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void admin_pide_una_unidad_inexistente_y_recibe_no_encontrado() {
        var vacio = new ConsultarEstadoDeCuentaService(id -> Optional.empty(), sub -> Set.of(), new PoliticaVisibilidad());
        var admin = new Solicitante("sub-admin", Set.of(Rol.ADMIN));
        assertThatThrownBy(() -> vacio.consultar(admin, otraUnidad)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}