package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

/** F8: el mismo comprobante bancario (banco + número de operación) no se registra dos veces, con la base real. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PagoOperacionDuplicadaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private static final String RESIDENTE = "00000000-0000-0000-0000-00000000a002";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static RequestPostProcessor residente() {
        return jwt().jwt(j -> j.subject(RESIDENTE)).authorities(new SimpleGrantedAuthority("ROLE_RESIDENTE"));
    }

    private ResultActions registrar(String clave, String banco, String nroOperacion) throws Exception {
        var unidad = jdbc.queryForObject("SELECT id FROM unidad WHERE codigo = 'A-101'", String.class);
        var nro = nroOperacion == null ? "" : ", nroOperacion: \\\"" + nroOperacion + "\\\"";
        var consulta = "mutation { registrarPago(unidadId: \\\"" + unidad + "\\\", monto: \\\"300.00\\\", fechaPago: \\\"2026-10-20\\\", banco: \\\""
                + banco + "\\\", idempotencyKey: \\\"" + clave + "\\\"" + nro + ") { id estado } }";
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta + "\"}")
                .with(residente()));
    }

    @Test
    void F8_el_mismo_comprobante_registrado_dos_veces_se_rechaza_con_F8_y_queda_un_solo_pago() throws Exception {
        var nro = "OP-" + UUID.randomUUID().toString().substring(0, 8);

        registrar(UUID.randomUUID().toString(), "Banco Unión", nro)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.registrarPago.estado").value("PENDIENTE"));
        registrar(UUID.randomUUID().toString(), "Banco Unión", nro)
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F8"));

        var cantidad = jdbc.queryForObject("SELECT count(*) FROM pago WHERE nro_operacion = ?", Integer.class, nro);
        assertThat(cantidad).isEqualTo(1);
    }

    @Test
    void O3_reintentar_la_misma_operacion_con_su_clave_devuelve_el_mismo_pago_y_no_es_duplicado() throws Exception {
        var nro = "OP-" + UUID.randomUUID().toString().substring(0, 8);
        var clave = UUID.randomUUID().toString();

        registrar(clave, "BNB", nro).andExpect(jsonPath("$.errors").doesNotExist());
        registrar(clave, "BNB", nro).andExpect(jsonPath("$.errors").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pago WHERE nro_operacion = ?", Integer.class, nro)).isEqualTo(1);
    }

    @Test
    void F8_un_pago_rechazado_libera_el_numero_de_operacion() throws Exception {
        var nro = "OP-" + UUID.randomUUID().toString().substring(0, 8);
        registrar(UUID.randomUUID().toString(), "BCP", nro).andExpect(jsonPath("$.errors").doesNotExist());
        jdbc.update(
                "UPDATE pago SET estado = 'RECHAZADO', motivo_rechazo = 'prueba', revisado_por = 'admin', revisado_en = now() "
                        + "WHERE nro_operacion = ?",
                nro);

        registrar(UUID.randomUUID().toString(), "BCP", nro)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.registrarPago.estado").value("PENDIENTE"));
    }

    @Test
    void F8_sin_numero_de_operacion_el_pago_se_registra_como_siempre() throws Exception {
        registrar(UUID.randomUUID().toString(), "Banco Ganadero", null)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.registrarPago.estado").value("PENDIENTE"));
    }
}
