package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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

/** {@code registrarPago} de punta a punta sobre los datos demo (el residente demo es titular de A-101). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PagosGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String RESIDENTE = "00000000-0000-0000-0000-00000000a002";

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

    private String unidad(String codigo) {
        return jdbc.queryForObject("SELECT id FROM unidad WHERE codigo = ?", String.class, codigo);
    }

    private static String registrar(String unidadId, String monto, String clave) {
        return "mutation { registrarPago(unidadId: \"" + unidadId + "\", monto: \"" + monto
                + "\", fechaPago: \"2026-10-20\", banco: \"Banco Unión\", idempotencyKey: \"" + clave
                + "\", horaDispositivo: \"2026-10-20T14:30:00-04:00\") { id unidadId monto fechaPago estado } }";
    }

    @Test
    void F9_residente_registra_un_pago_de_su_unidad_y_nace_pendiente() throws Exception {
        var clave = UUID.randomUUID().toString();

        graphql(token(RESIDENTE, "RESIDENTE"), registrar(unidad("A-101"), "390.00", clave))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.registrarPago.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.data.registrarPago.monto").value("390.00"))
                .andExpect(jsonPath("$.data.registrarPago.fechaPago").value("2026-10-20"));

        var registradoPor = jdbc.queryForObject(
                "SELECT registrado_por FROM pago WHERE idempotency_key = CAST(? AS uuid)", String.class, clave);
        assertThat(registradoPor).isEqualTo(RESIDENTE);
    }

    @Test
    void O3_reenviar_la_misma_clave_no_duplica_y_responde_lo_mismo() throws Exception {
        var clave = UUID.randomUUID().toString();

        var primera = graphql(token(RESIDENTE, "RESIDENTE"), registrar(unidad("A-101"), "120.50", clave))
                .andReturn().getResponse().getContentAsString();
        var segunda = graphql(token(RESIDENTE, "RESIDENTE"), registrar(unidad("A-101"), "120.50", clave))
                .andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primera);
        assertThat((String) JsonPath.read(primera, "$.data.registrarPago.estado")).isEqualTo("PENDIENTE");
        var cantidad = jdbc.queryForObject(
                "SELECT count(*) FROM pago WHERE idempotency_key = CAST(? AS uuid)", Integer.class, clave);
        assertThat(cantidad).isEqualTo(1);
    }

    @Test
    void F1_residente_no_registra_pagos_de_otra_unidad() throws Exception {
        graphql(token(RESIDENTE, "RESIDENTE"), registrar(unidad("B-101"), "100.00", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F1"));
    }

    @Test
    void guardia_no_registra_pagos() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a003", "GUARDIA"),
                        registrar(unidad("A-101"), "100.00", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}
