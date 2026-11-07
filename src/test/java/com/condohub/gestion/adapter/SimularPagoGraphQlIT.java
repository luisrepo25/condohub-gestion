package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
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

/** F7: {@code simularPago} muestra a qué cargos se aplicaría un pago, con la base real y sin guardar nada. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SimularPagoGraphQlIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String RESIDENTE = "00000000-0000-0000-0000-00000000a002";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor token(String sub, String rol) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private String unidad(String codigo) {
        return jdbc.queryForObject("SELECT id FROM unidad WHERE codigo = ?", String.class, codigo);
    }

    private ResultActions simular(RequestPostProcessor auth, String unidadId, String monto) throws Exception {
        var consulta = "{ simularPago(unidadId: \\\"" + unidadId + "\\\", monto: \\\"" + monto
                + "\\\") { excedente lineas { tipo concepto fechaVencimiento saldoActual aplicado saldoResultante } } }";
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta + "\"}")
                .with(auth));
    }

    @Test
    void F7_el_admin_ve_que_300_pagan_el_cargo_vencido_mas_antiguo_y_parte_del_siguiente() throws Exception {
        // B-101 debe septiembre y octubre (270.00 cada una) en los datos demo.
        simular(token("admin", "ADMIN"), unidad("B-101"), "300.00")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.simularPago.lineas[0].concepto").value("Expensa 09/2026"))
                .andExpect(jsonPath("$.data.simularPago.lineas[0].aplicado").value("270.00"))
                .andExpect(jsonPath("$.data.simularPago.lineas[0].saldoResultante").value("0.00"))
                .andExpect(jsonPath("$.data.simularPago.lineas[1].concepto").value("Expensa 10/2026"))
                .andExpect(jsonPath("$.data.simularPago.lineas[1].aplicado").value("30.00"))
                .andExpect(jsonPath("$.data.simularPago.lineas[1].saldoResultante").value("240.00"))
                .andExpect(jsonPath("$.data.simularPago.excedente").value("0.00"));
    }

    @Test
    void F9_simular_no_crea_pagos_ni_cambia_saldos() throws Exception {
        var pagosAntes = jdbc.queryForObject("SELECT count(*) FROM pago", Integer.class);
        var saldoAntes = jdbc.queryForObject("SELECT sum(saldo) FROM cargo", java.math.BigDecimal.class);

        simular(token("admin", "ADMIN"), unidad("B-101"), "300.00").andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pago", Integer.class)).isEqualTo(pagosAntes);
        assertThat(jdbc.queryForObject("SELECT sum(saldo) FROM cargo", java.math.BigDecimal.class)).isEqualByComparingTo(saldoAntes);
    }

    @Test
    void F7_una_unidad_sin_deuda_deja_todo_el_pago_como_saldo_a_favor() throws Exception {
        simular(token(RESIDENTE, "RESIDENTE"), unidad("A-101"), "300.00")
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.simularPago.lineas").isEmpty())
                .andExpect(jsonPath("$.data.simularPago.excedente").value("300.00"));
    }

    @Test
    void F1_el_residente_no_simula_pagos_de_otra_unidad() throws Exception {
        simular(token(RESIDENTE, "RESIDENTE"), unidad("B-101"), "300.00")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F1"));
    }

    @Test
    void F10_un_monto_en_cero_se_rechaza() throws Exception {
        simular(token("admin", "ADMIN"), unidad("B-101"), "0.00")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F10"));
    }

    @Test
    void el_guardia_no_puede_simular_pagos() throws Exception {
        simular(token("guardia", "GUARDIA"), unidad("B-101"), "300.00")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("FORBIDDEN"));
    }
}
