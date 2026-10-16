package com.condohub.gestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.condohub.gestion.application.usecase.ConsultarMorososService;
import com.condohub.gestion.domain.model.Moroso;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsultarMorososServiceTest {

    private static Moroso moroso(String codigo, String deuda) {
        return new Moroso(UUID.randomUUID(), codigo, "B", Dinero.de(deuda), 2, "Titular " + codigo, codigo + "@x.test", null);
    }

    @Test
    void F4_los_morosos_salen_de_mayor_a_menor_deuda_vencida() {
        var servicio = new ConsultarMorososService(
                () -> List.of(moroso("B-101", "270.00"), moroso("B-102", "540.00"), moroso("B-103", "270.00")));

        assertThat(servicio.consultar()).extracting(Moroso::codigo).containsExactly("B-102", "B-101", "B-103");
    }

    @Test
    void sin_morosos_devuelve_lista_vacia() {
        assertThat(new ConsultarMorososService(List::of).consultar()).isEmpty();
    }
}