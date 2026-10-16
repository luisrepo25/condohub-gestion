package com.condohub.gestion.adapter;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code miPerfil} de punta a punta con PostgreSQL real y los datos de demostración. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PerfilGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String CONSULTA = "{ miPerfil { sub roles personaId nombre unidades { codigo bloque titular } } }";

    @Autowired MockMvc mvc;

    private static RequestPostProcessor token(String sub, String rol) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private ResultActions miPerfil(RequestPostProcessor auth) throws Exception {
        var peticion = post("/graphql").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + CONSULTA + "\"}");
        return mvc.perform(auth == null ? peticion : peticion.with(auth));
    }

    @Test
    void sin_token_responde_401() throws Exception {
        miPerfil(null).andExpect(status().isUnauthorized());
    }

    @Test
    void F1_residente_titular_de_A101_ve_su_persona_y_su_unidad() throws Exception {
        miPerfil(token("00000000-0000-0000-0000-00000000a002", "RESIDENTE"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.miPerfil.roles[0]").value("RESIDENTE"))
                .andExpect(jsonPath("$.data.miPerfil.nombre").value("Residente Demo"))
                .andExpect(jsonPath("$.data.miPerfil.personaId").isString())
                .andExpect(jsonPath("$.data.miPerfil.unidades", hasSize(1)))
                .andExpect(jsonPath("$.data.miPerfil.unidades[0].codigo").value("A-101"))
                .andExpect(jsonPath("$.data.miPerfil.unidades[0].titular").value(true));
    }

    @Test
    void admin_sin_persona_enlazada_recibe_unidades_vacias() throws Exception {
        miPerfil(token("00000000-0000-0000-0000-00000000a001", "ADMIN"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.miPerfil.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.miPerfil.personaId").doesNotExist())
                .andExpect(jsonPath("$.data.miPerfil.unidades", hasSize(0)));
    }

    @Test
    void guardia_tambien_puede_pedir_su_perfil() throws Exception {
        miPerfil(token("00000000-0000-0000-0000-00000000a003", "GUARDIA"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.miPerfil.roles[0]").value("GUARDIA"));
    }
}
