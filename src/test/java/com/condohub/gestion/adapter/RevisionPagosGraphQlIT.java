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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code confirmarPago} y {@code rechazarPago} de punta a punta sobre los datos demo (F7, F8). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RevisionPagosGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String ADMIN = "00000000-0000-0000-0000-00000000a001";
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
        return jdbc.queryForObject("SELECT id::text FROM unidad WHERE codigo = ?", String.class, codigo);
    }

    /** Un pago PENDIENTE como lo dejaría registrarPago. */
    private String pagoPendiente(String unidadId, BigDecimal monto) {
        return jdbc.queryForObject(
                """
                INSERT INTO pago (unidad_id, monto, fecha_pago, idempotency_key, registrado_por)
                VALUES (?::uuid, ?, current_date, ?, 'sub-res') RETURNING id::text
                """,
                String.class,
                unidadId,
                monto,
                UUID.randomUUID());
    }

    private BigDecimal deudaPendiente(String unidadId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(sum(saldo), 0) FROM cargo WHERE unidad_id = ?::uuid AND estado = 'PENDIENTE'",
                BigDecimal.class,
                unidadId);
    }

    private static String confirmar(String pagoId) {
        return "mutation { confirmarPago(pagoId: \"" + pagoId
                + "\") { pago { id estado } reciboNumero imputado saldoAFavor } }";
    }

    @Test
    void F7_F8_confirmar_paga_toda_la_deuda_deja_el_excedente_y_emite_recibo() throws Exception {
        var a101 = unidad("A-101");
        var deuda = deudaPendiente(a101);
        var pagoId = pagoPendiente(a101, deuda.add(new BigDecimal("50.00")));

        graphql(token(ADMIN, "ADMIN"), confirmar(pagoId))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.confirmarPago.pago.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.data.confirmarPago.imputado").value(deuda.setScale(2).toPlainString()))
                .andExpect(jsonPath("$.data.confirmarPago.saldoAFavor").value("50.00"));

        assertThat(deudaPendiente(a101)).isEqualByComparingTo("0");
        var recibos = jdbc.queryForObject("SELECT count(*) FROM recibo WHERE pago_id = ?::uuid", Integer.class, pagoId);
        assertThat(recibos).isEqualTo(1);
    }

    @Test
    void F7_un_pago_parcial_salda_primero_el_cargo_que_vence_antes() throws Exception {
        var unidadId = jdbc.queryForObject(
                """
                SELECT unidad_id::text FROM cargo c JOIN unidad u ON u.id = c.unidad_id
                 WHERE c.estado = 'PENDIENTE' AND u.codigo <> 'A-101'
                 GROUP BY unidad_id HAVING count(*) >= 2 LIMIT 1
                """,
                String.class);
        var cargos = jdbc.queryForList(
                """
                SELECT id::text AS id, saldo FROM cargo WHERE unidad_id = ?::uuid AND estado = 'PENDIENTE'
                 ORDER BY fecha_vencimiento, creado_en
                """,
                unidadId);
        var masAntiguo = (String) cargos.get(0).get("id");
        var siguiente = (String) cargos.get(1).get("id");
        var saldoSiguiente = (BigDecimal) cargos.get(1).get("saldo");
        var pagoId = pagoPendiente(unidadId, ((BigDecimal) cargos.get(0).get("saldo")).add(new BigDecimal("0.01")));

        graphql(token(ADMIN, "ADMIN"), confirmar(pagoId)).andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT estado::text FROM cargo WHERE id = ?::uuid", String.class, masAntiguo))
                .isEqualTo("PAGADO");
        assertThat(jdbc.queryForObject("SELECT saldo FROM cargo WHERE id = ?::uuid", BigDecimal.class, siguiente))
                .isEqualByComparingTo(saldoSiguiente.subtract(new BigDecimal("0.01")));
    }

    @Test
    void F8_un_pago_confirmado_no_se_vuelve_a_confirmar() throws Exception {
        var pagoId = pagoPendiente(unidad("B-101"), new BigDecimal("10.00"));
        graphql(token(ADMIN, "ADMIN"), confirmar(pagoId)).andExpect(jsonPath("$.errors").doesNotExist());

        graphql(token(ADMIN, "ADMIN"), confirmar(pagoId)).andExpect(jsonPath("$.errors[0].extensions.code").value("F8"));
    }

    @Test
    void F8_rechazar_exige_motivo_y_guarda_el_motivo() throws Exception {
        var pagoId = pagoPendiente(unidad("B-102"), new BigDecimal("10.00"));

        graphql(token(ADMIN, "ADMIN"), "mutation { rechazarPago(pagoId: \"" + pagoId + "\", motivo: \" \") { estado } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F8"));

        graphql(token(ADMIN, "ADMIN"), "mutation { rechazarPago(pagoId: \"" + pagoId
                        + "\", motivo: \"No figura en el extracto\") { estado motivoRechazo } }")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.rechazarPago.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.data.rechazarPago.motivoRechazo").value("No figura en el extracto"));

        graphql(token(ADMIN, "ADMIN"), confirmar(pagoId)).andExpect(jsonPath("$.errors[0].extensions.code").value("F8"));
    }

    @Test
    void solo_admin_confirma_pagos() throws Exception {
        var pagoId = pagoPendiente(unidad("A-101"), new BigDecimal("10.00"));

        graphql(token(RESIDENTE, "RESIDENTE"), confirmar(pagoId))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }

    @Test
    void confirmar_un_pago_que_no_existe_es_NOT_FOUND() throws Exception {
        graphql(token(ADMIN, "ADMIN"), confirmar(UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("NOT_FOUND"));
    }
}
