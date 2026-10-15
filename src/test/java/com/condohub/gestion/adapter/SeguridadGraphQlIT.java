package com.condohub.gestion.adapter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Roles y regla F1 de punta a punta: PostgreSQL real con los datos de demostración y el pipeline de seguridad
 * completo. Los tokens se simulan con los mismos {@code sub} y roles del realm de Keycloak.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SeguridadGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String SUB_RESIDENTE = "00000000-0000-0000-0000-00000000a002"; // titular de A-101

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor token(String sub, String rol) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private UUID unidad(String codigo) {
        return jdbc.queryForObject("SELECT id FROM unidad WHERE codigo = ?", UUID.class, codigo);
    }

    private org.springframework.test.web.servlet.ResultActions graphql(RequestPostProcessor auth, String consulta)
            throws Exception {
        var cuerpo = "{\"query\":\"" + consulta.replace("\"", "\\\"") + "\"}";
        var peticion = post("/graphql").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mvc.perform(auth == null ? peticion : peticion.with(auth));
    }

    @Test
    void sin_token_responde_401_en_problem_json() throws Exception {
        graphql(null, "{ salud { estado } }")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void cualquier_rol_autenticado_ve_la_salud() throws Exception {
        graphql(token("x", "GUARDIA"), "{ salud { servicio estado } }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.salud.servicio").value("gestion"));
    }

    @Test
    void F1_residente_ve_su_unidad_con_dinero_como_string() throws Exception {
        graphql(token(SUB_RESIDENTE, "RESIDENTE"),
                        "{ estadoDeCuenta(unidadId: \"" + unidad("A-101") + "\") { codigo deudaTotal moroso } }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.estadoDeCuenta.codigo").value("A-101"))
                .andExpect(jsonPath("$.data.estadoDeCuenta.deudaTotal").isString());
    }

    @Test
    void F1_residente_no_ve_otra_unidad() throws Exception {
        graphql(token(SUB_RESIDENTE, "RESIDENTE"),
                        "{ estadoDeCuenta(unidadId: \"" + unidad("B-101") + "\") { codigo } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F1"));
    }

    @Test
    void F1_admin_ve_cualquier_unidad() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a001", "ADMIN"),
                        "{ estadoDeCuenta(unidadId: \"" + unidad("B-101") + "\") { codigo } }")
                .andExpect(jsonPath("$.data.estadoDeCuenta.codigo").value("B-101"));
    }

    @Test
    void guardia_no_puede_ver_estados_de_cuenta() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a003", "GUARDIA"),
                        "{ estadoDeCuenta(unidadId: \"" + unidad("A-101") + "\") { codigo } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }

    @Test
    void solo_admin_ve_los_pagos_pendientes() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a001", "ADMIN"), "{ pagosPendientes { id monto estado } }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.pagosPendientes", hasSize(2)))
                .andExpect(jsonPath("$.data.pagosPendientes[0].estado").value("PENDIENTE"));

        graphql(token(SUB_RESIDENTE, "RESIDENTE"), "{ pagosPendientes { id } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}