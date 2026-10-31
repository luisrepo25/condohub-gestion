package com.condohub.gestion.adapter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Un identificador que no es UUID (por ejemplo el código «B-102» de una unidad) es un error del cliente: debe llegar como
 * {@code BAD_REQUEST} con un mensaje claro y no como {@code INTERNAL_ERROR}, para que quien llama (el agente, un LLM) pueda corregirse.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class IdentificadorInvalidoGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @Autowired MockMvc mvc;

    private ResultActions consultar(String consulta) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta.replace("\"", "\\\"") + "\"}")
                .with(jwt().jwt(j -> j.subject("sub-admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void un_codigo_de_unidad_en_lugar_de_un_UUID_es_BAD_REQUEST_y_explica_el_formato() throws Exception {
        consultar("{ estadoDeCuenta(unidadId: \"B-102\") { codigo } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("UUID")));
    }

    @Test
    void un_UUID_valido_que_no_existe_sigue_siendo_NOT_FOUND() throws Exception {
        consultar("{ estadoDeCuenta(unidadId: \"00000000-0000-4000-8000-000000000000\") { codigo } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("NOT_FOUND"));
    }
}
