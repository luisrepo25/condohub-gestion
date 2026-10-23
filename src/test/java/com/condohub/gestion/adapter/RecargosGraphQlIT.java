package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Recargos por mora de punta a punta sobre los datos demo (F5): proponer no cobra; confirmar crea el cargo. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RecargosGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String ADMIN = "00000000-0000-0000-0000-00000000a001";
    private static final String CAMPOS = "{ id cargoOrigenId periodo porcentaje base monto estado origen motivo cargoGeneradoId }";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor token(String sub, String rol) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private ResultActions graphql(RequestPostProcessor auth, String consulta) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta.replace("\"", "\\\"") + "\"}")
                .with(auth));
    }

    /** Una unidad morosa distinta en cada test, para que no se pisen. */
    private String unidadMorosa(int indice) {
        return jdbc.queryForList(
                        """
                        SELECT DISTINCT c.unidad_id::text FROM cargo c
                         WHERE c.estado = 'PENDIENTE' AND c.tipo <> 'RECARGO' AND c.fecha_vencimiento < current_date
                         ORDER BY 1
                        """,
                        String.class)
                .get(indice);
    }

    private BigDecimal recargoEsperado(String unidadId) {
        List<BigDecimal> saldos = jdbc.queryForList(
                """
                SELECT saldo FROM cargo WHERE unidad_id = ?::uuid AND estado = 'PENDIENTE' AND tipo <> 'RECARGO'
                   AND fecha_vencimiento < current_date
                """,
                BigDecimal.class,
                unidadId);
        return saldos.stream()
                .map(s -> s.multiply(new BigDecimal("2.00")).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int cargosRecargo(String unidadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM cargo WHERE unidad_id = ?::uuid AND tipo = 'RECARGO'", Integer.class, unidadId);
    }

    @Test
    void F5_proponer_no_cobra_y_repetirlo_no_duplica() throws Exception {
        var unidad = unidadMorosa(0);
        var esperado = recargoEsperado(unidad);

        var respuesta = graphql(token(ADMIN, "ADMIN"),
                        "mutation { proponerRecargo(unidadId: \"" + unidad + "\", monto: \"" + esperado.toPlainString()
                                + "\", motivo: \"Mora del mes\", misionId: \"mision-1\") " + CAMPOS + " }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.proponerRecargo[0].estado").value("PROPUESTO"))
                .andExpect(jsonPath("$.data.proponerRecargo[0].origen").value("AGENTE"))
                .andExpect(jsonPath("$.data.proponerRecargo[0].porcentaje").value("2.00"))
                .andReturn().getResponse().getContentAsString();
        int propuestas = JsonPath.<List<?>>read(respuesta, "$.data.proponerRecargo").size();

        assertThat(cargosRecargo(unidad)).isZero();
        graphql(token(ADMIN, "ADMIN"), "mutation { proponerRecargo(unidadId: \"" + unidad + "\") " + CAMPOS + " }")
                .andExpect(jsonPath("$.data.proponerRecargo.length()").value(propuestas));
        graphql(token(ADMIN, "ADMIN"), "{ recargosPropuestos { id unidadId } }")
                .andExpect(jsonPath("$.data.recargosPropuestos[?(@.unidadId == '" + unidad + "')]").exists());
    }

    @Test
    void F5_un_monto_distinto_al_calculado_se_rechaza() throws Exception {
        var unidad = unidadMorosa(1);

        graphql(token(ADMIN, "ADMIN"),
                        "mutation { proponerRecargo(unidadId: \"" + unidad + "\", monto: \"99999.00\") " + CAMPOS + " }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F5"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM recargo_propuesto WHERE unidad_id = ?::uuid", Integer.class, unidad))
                .isZero();
    }

    @Test
    void F5_confirmar_crea_el_cargo_recargo_y_no_se_confirma_dos_veces() throws Exception {
        var unidad = unidadMorosa(2);
        var respuesta = graphql(token(ADMIN, "ADMIN"),
                        "mutation { proponerRecargo(unidadId: \"" + unidad + "\") " + CAMPOS + " }")
                .andReturn().getResponse().getContentAsString();
        String propuestaId = JsonPath.read(respuesta, "$.data.proponerRecargo[0].id");
        String monto = JsonPath.read(respuesta, "$.data.proponerRecargo[0].monto");

        var confirmado = graphql(token(ADMIN, "ADMIN"),
                        "mutation { confirmarRecargo(propuestaId: \"" + propuestaId + "\") " + CAMPOS + " }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.confirmarRecargo.estado").value("CONFIRMADO"))
                .andReturn().getResponse().getContentAsString();
        String cargoId = JsonPath.read(confirmado, "$.data.confirmarRecargo.cargoGeneradoId");

        var cargo = jdbc.queryForMap("SELECT tipo::text AS tipo, monto FROM cargo WHERE id = ?::uuid", cargoId);
        assertThat(cargo.get("tipo")).isEqualTo("RECARGO");
        assertThat((BigDecimal) cargo.get("monto")).isEqualByComparingTo(monto);

        graphql(token(ADMIN, "ADMIN"), "mutation { confirmarRecargo(propuestaId: \"" + propuestaId + "\") { estado } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F5"));
    }

    @Test
    void F5_rechazar_no_cobra_y_guarda_el_motivo() throws Exception {
        var unidad = unidadMorosa(3);
        var respuesta = graphql(token(ADMIN, "ADMIN"),
                        "mutation { proponerRecargo(unidadId: \"" + unidad + "\", motivo: \"Mora\") " + CAMPOS + " }")
                .andReturn().getResponse().getContentAsString();
        String propuestaId = JsonPath.read(respuesta, "$.data.proponerRecargo[0].id");

        graphql(token(ADMIN, "ADMIN"), "mutation { rechazarRecargo(propuestaId: \"" + propuestaId
                        + "\", motivo: \"Acordó plan de pago\") " + CAMPOS + " }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.rechazarRecargo.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.data.rechazarRecargo.motivo").value("Mora | Rechazo: Acordó plan de pago"))
                .andExpect(jsonPath("$.data.rechazarRecargo.cargoGeneradoId").doesNotExist());
        assertThat(cargosRecargo(unidad)).isZero();
    }

    @Test
    void F5_una_unidad_sin_saldo_vencido_no_recibe_recargo() throws Exception {
        var alDia = jdbc.queryForObject(
                """
                SELECT u.id::text FROM unidad u
                 WHERE NOT EXISTS (SELECT 1 FROM cargo c WHERE c.unidad_id = u.id AND c.estado = 'PENDIENTE'
                                     AND c.fecha_vencimiento < current_date)
                 ORDER BY u.codigo LIMIT 1
                """,
                String.class);

        graphql(token(ADMIN, "ADMIN"), "mutation { proponerRecargo(unidadId: \"" + alDia + "\") { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F5"));
    }

    @Test
    void solo_admin_propone_recargos() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a002", "RESIDENTE"),
                        "mutation { proponerRecargo(unidadId: \"" + unidadMorosa(0) + "\") { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}
