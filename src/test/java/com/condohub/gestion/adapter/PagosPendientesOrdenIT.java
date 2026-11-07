package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** F8: la conciliación muestra primero los pagos registrados más recientemente, aunque lleven la misma fecha de comprobante. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PagosPendientesOrdenIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID insertarPago(String unidad, int horasDesdeAhora) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO pago (id, unidad_id, monto, fecha_pago, banco, idempotency_key, registrado_por, registrado_en)
                VALUES (?, (SELECT id FROM unidad WHERE codigo = ?), 800.00, DATE '2026-11-05', 'Banco Unión', ?, 'test',
                        now() + make_interval(hours => ?))
                """,
                id, unidad, UUID.randomUUID(), horasDesdeAhora);
        return id;
    }

    @Test
    void F8_los_pagos_pendientes_salen_del_mas_reciente_al_mas_antiguo() throws Exception {
        UUID antiguo = insertarPago("A-101", 1);
        UUID reciente = insertarPago("A-101", 2);

        String cuerpo = mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ pagosPendientes { id } }\"}")
                        .with(jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> ids = JsonPath.read(cuerpo, "$.data.pagosPendientes[*].id");
        assertThat(ids.get(0)).isEqualTo(reciente.toString());
        assertThat(ids.get(1)).isEqualTo(antiguo.toString());
    }
}
