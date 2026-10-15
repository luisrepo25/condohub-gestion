package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PoliticaVisibilidadTest {

    private final PoliticaVisibilidad politica = new PoliticaVisibilidad();
    private final UUID miUnidad = UUID.randomUUID();
    private final UUID otraUnidad = UUID.randomUUID();

    @Test
    void F1_residente_ve_el_estado_de_cuenta_de_su_unidad() {
        var residente = new Solicitante("sub-1", Set.of(Rol.RESIDENTE));
        assertThatCode(() -> politica.verificarPuedeVerEstadoDeCuenta(residente, miUnidad, Set.of(miUnidad)))
                .doesNotThrowAnyException();
    }

    @Test
    void F1_residente_no_ve_el_estado_de_cuenta_de_otra_unidad() {
        var residente = new Solicitante("sub-1", Set.of(Rol.RESIDENTE));
        assertThatThrownBy(() -> politica.verificarPuedeVerEstadoDeCuenta(residente, otraUnidad, Set.of(miUnidad)))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> org.assertj.core.api.Assertions
                        .assertThat(e.codigo()).isEqualTo("F1"));
    }

    @Test
    void F1_admin_ve_el_estado_de_cuenta_de_cualquier_unidad() {
        var admin = new Solicitante("sub-admin", Set.of(Rol.ADMIN));
        assertThatCode(() -> politica.verificarPuedeVerEstadoDeCuenta(admin, otraUnidad, Set.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void F1_guardia_no_ve_estados_de_cuenta_aunque_figure_como_residente_de_la_unidad() {
        var guardia = new Solicitante("sub-g", Set.of(Rol.GUARDIA));
        assertThatThrownBy(() -> politica.verificarPuedeVerEstadoDeCuenta(guardia, miUnidad, Set.of(miUnidad)))
                .isInstanceOf(ReglaNegocioVioladaException.class);
    }
}