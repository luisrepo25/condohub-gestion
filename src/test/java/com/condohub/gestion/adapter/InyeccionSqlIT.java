package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;
import java.util.List;
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

/**
 * Evidencia repetible contra inyección SQL: los ataques clásicos entran por la API real (GraphQL) con la base real.
 * Esperado: el texto malicioso se guarda tal cual como dato, no se ejecuta, no cambia el resultado de otras consultas
 * y las tablas siguen intactas.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InyeccionSqlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final List<String> ATAQUES = List.of(
            "' OR '1'='1",
            "'; DROP TABLE pago; --",
            "x' UNION SELECT usename FROM pg_user --",
            "1; DELETE FROM cargo; --",
            "\\\" OR 1=1 --");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ResultActions graphql(String consulta) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}")
                .with(admin()));
    }

    private String unidad(String codigo) {
        return jdbc.queryForObject("SELECT id FROM unidad WHERE codigo = ?", String.class, codigo);
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    @Test
    void ataques_en_el_banco_y_el_numero_de_operacion_se_guardan_como_texto_y_no_se_ejecutan() throws Exception {
        int pagosAntes = contar("pago");
        int cargosAntes = contar("cargo");
        String ayer = LocalDate.now().minusDays(1).toString();

        for (String ataque : ATAQUES) {
            UUID llave = UUID.randomUUID();
            String seguro = ataque.replace("\\", "\\\\").replace("\"", "\\\"");
            graphql("mutation { registrarPago(unidadId: \"" + unidad("A-101") + "\", monto: \"10.00\", fechaPago: \"" + ayer
                            + "\", banco: \"" + seguro + "\", nroOperacion: \"" + seguro + "\", idempotencyKey: \"" + llave
                            + "\") { estado } }")
                    .andExpect(jsonPath("$.errors").doesNotExist());

            assertThat(jdbc.queryForObject("SELECT banco FROM pago WHERE idempotency_key = ?::uuid", String.class, llave))
                    .isEqualTo(ataque);
        }

        assertThat(contar("pago")).isEqualTo(pagosAntes + ATAQUES.size());
        assertThat(contar("cargo")).isEqualTo(cargosAntes);
    }

    @Test
    void un_ataque_como_numero_de_operacion_no_se_confunde_con_el_de_otro_pago() throws Exception {
        String ayer = LocalDate.now().minusDays(1).toString();
        // Si la comparación del F8 concatenara el texto, «' OR '1'='1» coincidiría con cualquier pago y daría F8.
        for (int i = 0; i < 2; i++) {
            graphql("mutation { registrarPago(unidadId: \"" + unidad("A-101") + "\", monto: \"10.00\", fechaPago: \"" + ayer
                            + "\", banco: \"BNB\", nroOperacion: \"x' OR '1'='1\", idempotencyKey: \"" + UUID.randomUUID()
                            + "\") { estado } }")
                    .andExpect(i == 0
                            ? jsonPath("$.errors").doesNotExist()
                            : jsonPath("$.errors[0].extensions.code").value("F8")); // el segundo SÍ es el mismo texto literal
        }
        graphql("mutation { registrarPago(unidadId: \"" + unidad("A-101") + "\", monto: \"10.00\", fechaPago: \"" + ayer
                        + "\", banco: \"BNB\", nroOperacion: \"otro-numero\", idempotencyKey: \"" + UUID.randomUUID()
                        + "\") { estado } }")
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void ataques_donde_va_un_identificador_se_rechazan_como_argumento_invalido_sin_tocar_la_base() throws Exception {
        int cargosAntes = contar("cargo");
        for (String ataque : ATAQUES) {
            String seguro = ataque.replace("\\", "\\\\").replace("\"", "\\\"");
            graphql("{ estadoDeCuenta(unidadId: \"" + seguro + "\") { codigo } }")
                    .andExpect(jsonPath("$.data.estadoDeCuenta").doesNotExist());
            graphql("{ simularPago(unidadId: \"" + seguro + "\", monto: \"10.00\") { excedente } }")
                    .andExpect(jsonPath("$.data.simularPago").doesNotExist());
        }
        assertThat(contar("cargo")).isEqualTo(cargosAntes);
        assertThat(contar("pago")).isPositive();
    }

    @Test
    void ataques_en_el_motivo_de_rechazo_se_guardan_como_texto() throws Exception {
        UUID pago = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO pago (id, unidad_id, monto, fecha_pago, banco, idempotency_key, registrado_por) "
                        + "VALUES (?, ?::uuid, 5.00, ?, 'BNB', ?, 'test')",
                pago, unidad("A-102"), java.sql.Date.valueOf(LocalDate.now().minusDays(1)), UUID.randomUUID());

        graphql("mutation { rechazarPago(pagoId: \"" + pago + "\", motivo: \"'; DROP TABLE recibo; --\") { id } }")
                .andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT motivo_rechazo FROM pago WHERE id = ?", String.class, pago))
                .isEqualTo("'; DROP TABLE recibo; --");
        assertThat(contar("recibo")).isGreaterThanOrEqualTo(0); // la tabla sigue existiendo
    }
}
