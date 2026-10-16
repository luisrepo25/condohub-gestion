package com.condohub.gestion.adapter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

/** {@code morosos} de punta a punta con PostgreSQL real y los datos de demostración. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MorososGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String CONSULTA =
            "{ morosos { codigo deudaVencida expensasVencidas propietarioNombre propietarioEmail } }";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor token(String rol) {
        return jwt().jwt(j -> j.subject("sub-" + rol)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private ResultActions morosos(String rol) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + CONSULTA + "\"}")
                .with(token(rol)));
    }

    @Test
    void F4_admin_recibe_las_unidades_morosas_con_el_contacto_del_titular() throws Exception {
        int esperadas = jdbc.queryForObject("SELECT count(*) FROM v_estado_cuenta WHERE moroso", Integer.class);

        morosos("ADMIN")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.morosos", hasSize(esperadas)))
                .andExpect(jsonPath("$.data.morosos[0].deudaVencida").isString())
                .andExpect(jsonPath("$.data.morosos[0].propietarioEmail").isString());
    }

    @Test
    void solo_admin_puede_consultar_morosos() throws Exception {
        morosos("RESIDENTE").andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
        morosos("GUARDIA").andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}