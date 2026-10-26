package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** F7 de punta a punta: el saldo a favor de una unidad paga sola su expensa nueva al emitir el periodo. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SaldoAFavorGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void F7_al_emitir_el_saldo_a_favor_descuenta_la_expensa_nueva() throws Exception {
        // Una unidad al día (sin cargos pendientes) con 100.00 a favor de un pago confirmado.
        var unidadId = jdbc.queryForObject(
                """
                SELECT u.id::text FROM unidad u
                 WHERE u.codigo <> 'A-101'
                   AND NOT EXISTS (SELECT 1 FROM cargo c WHERE c.unidad_id = u.id AND c.estado = 'PENDIENTE')
                 ORDER BY u.codigo LIMIT 1
                """,
                String.class);
        var pagoId = jdbc.queryForObject(
                """
                INSERT INTO pago (unidad_id, monto, fecha_pago, estado, idempotency_key, registrado_por,
                                  revisado_por, revisado_en)
                VALUES (?::uuid, 100.00, current_date, 'CONFIRMADO', ?, 'sub-res', 'sub-admin', now())
                RETURNING id::text
                """,
                String.class,
                unidadId,
                UUID.randomUUID());

        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { emitirExpensas(periodo: \\\"2031-06\\\", presupuesto: \\\"60000\\\")"
                                + " { unidades } }\"}")
                        .with(jwt().jwt(j -> j.subject("00000000-0000-0000-0000-00000000a001"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(jsonPath("$.errors").doesNotExist());

        var expensa = jdbc.queryForMap(
                """
                SELECT c.monto, c.saldo FROM cargo c JOIN periodo_expensas p ON p.id = c.periodo_id
                 WHERE c.unidad_id = ?::uuid AND p.periodo = DATE '2031-06-01'
                """,
                unidadId);
        assertThat((BigDecimal) expensa.get("saldo"))
                .isEqualByComparingTo(((BigDecimal) expensa.get("monto")).subtract(new BigDecimal("100.00")));

        var saldoAFavor = jdbc.queryForObject(
                "SELECT saldo_a_favor FROM v_estado_cuenta WHERE unidad_id = ?::uuid", BigDecimal.class, unidadId);
        assertThat(saldoAFavor).isEqualByComparingTo("0");
        var imputado = jdbc.queryForObject(
                "SELECT sum(monto) FROM imputacion_pago WHERE pago_id = ?::uuid", BigDecimal.class, pagoId);
        assertThat(imputado).isEqualByComparingTo("100.00");
    }
}
