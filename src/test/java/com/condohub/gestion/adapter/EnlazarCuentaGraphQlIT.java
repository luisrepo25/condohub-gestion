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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code enlazarCuenta} de punta a punta: solo ADMIN, y el residente enlazado ve luego su unidad con {@code miPerfil}. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EnlazarCuentaGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

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

    @Test
    void F1_admin_enlaza_una_cuenta_y_esa_cuenta_ve_su_unidad() throws Exception {
        var persona = jdbc.queryForObject(
                "SELECT p.id FROM persona p JOIN unidad u ON u.propietario_titular_id = p.id WHERE u.codigo = 'B-101'",
                String.class);

        graphql(token("00000000-0000-0000-0000-00000000a001", "ADMIN"),
                        "mutation { enlazarCuenta(personaId: \"" + persona + "\", keycloakSub: \"sub-nuevo\") { keycloakSub } }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.enlazarCuenta.keycloakSub").value("sub-nuevo"));

        graphql(token("sub-nuevo", "RESIDENTE"), "{ miPerfil { unidades { codigo titular } } }")
                .andExpect(jsonPath("$.data.miPerfil.unidades[0].codigo").value("B-101"));
    }

    @Test
    void F1_una_cuenta_ya_enlazada_no_se_reasigna() throws Exception {
        var persona = jdbc.queryForObject(
                "SELECT p.id FROM persona p JOIN unidad u ON u.propietario_titular_id = p.id WHERE u.codigo = 'B-102'",
                String.class);

        graphql(token("00000000-0000-0000-0000-00000000a001", "ADMIN"),
                        "mutation { enlazarCuenta(personaId: \"" + persona
                                + "\", keycloakSub: \"00000000-0000-0000-0000-00000000a002\") { keycloakSub } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F1"));
    }

    @Test
    void solo_admin_puede_enlazar_cuentas() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a002", "RESIDENTE"),
                        "mutation { enlazarCuenta(personaId: \"00000000-0000-0000-0000-000000000001\", keycloakSub: \"x\") { keycloakSub } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}