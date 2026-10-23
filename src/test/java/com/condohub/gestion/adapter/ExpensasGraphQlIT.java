package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
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

/** {@code emitirExpensas} de punta a punta sobre los datos demo (200 unidades; agosto a octubre ya emitidos). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ExpensasGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String ADMIN = "00000000-0000-0000-0000-00000000a001";

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

    private static String emitir(String periodo, String presupuesto) {
        return "mutation { emitirExpensas(periodo: \"" + periodo + "\", presupuesto: \"" + presupuesto
                + "\") { periodo presupuesto fechaVencimiento unidades total } }";
    }

    @Test
    void F3_admin_emite_las_expensas_de_un_periodo_nuevo() throws Exception {
        graphql(token(ADMIN, "ADMIN"), emitir("2026-12", "60000"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.emitirExpensas.periodo").value("2026-12"))
                .andExpect(jsonPath("$.data.emitirExpensas.fechaVencimiento").value("2026-12-10"))
                .andExpect(jsonPath("$.data.emitirExpensas.unidades").value(200))
                .andExpect(jsonPath("$.data.emitirExpensas.total").value("60000.00"));

        var suma = jdbc.queryForObject(
                "SELECT sum(c.monto) FROM cargo c JOIN periodo_expensas p ON p.id = c.periodo_id"
                        + " WHERE p.periodo = DATE '2026-12-01' AND c.tipo = 'EXPENSA'",
                BigDecimal.class);
        assertThat(suma).isEqualByComparingTo("60000.00");
    }

    @Test
    void F3_un_periodo_ya_emitido_responde_F3() throws Exception {
        graphql(token(ADMIN, "ADMIN"), emitir("2026-10", "60000"))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F3"));
    }

    @Test
    void F10_presupuesto_con_mas_de_dos_decimales_responde_F10() throws Exception {
        graphql(token(ADMIN, "ADMIN"), emitir("2027-01", "100.123"))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F10"));
    }

    @Test
    void solo_admin_puede_emitir_expensas() throws Exception {
        graphql(token("00000000-0000-0000-0000-00000000a002", "RESIDENTE"), emitir("2027-02", "60000"))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}
