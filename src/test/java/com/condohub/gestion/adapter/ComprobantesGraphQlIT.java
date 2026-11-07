package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;
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

/** F8 (foto del comprobante): el pago se enlaza a su foto con la base real; sin S3 configurado todo sigue andando. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ComprobantesGraphQlIT {

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

    private ResultActions graphql(RequestPostProcessor auth, String consulta) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta.replace("\"", "\\\"") + "\"}")
                .with(auth));
    }

    private ResultActions registrar(String llave, String comprobanteKey) throws Exception {
        String ayer = LocalDate.now().minusDays(1).toString();
        return graphql(
                token(RESIDENTE, "RESIDENTE"),
                "mutation { registrarPago(unidadId: \"" + unidad("A-101") + "\", monto: \"50.00\", fechaPago: \"" + ayer
                        + "\", banco: \"BNB\", idempotencyKey: \"" + llave + "\", comprobanteKey: \"" + comprobanteKey
                        + "\") { id estado } }");
    }

    @Test
    void F8_el_pago_registrado_con_foto_guarda_la_clave_en_la_base() throws Exception {
        String llave = UUID.randomUUID().toString();
        String clave = "comprobantes/A-101/" + llave + ".jpg";

        registrar(llave, clave)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.data.registrarPago.estado").value("PENDIENTE"));

        assertThat(jdbc.queryForObject("SELECT comprobante_key FROM pago WHERE idempotency_key = ?::uuid", String.class, llave))
                .isEqualTo(clave);
    }

    @Test
    void F8_una_foto_de_la_carpeta_de_otra_unidad_se_rechaza_y_no_se_registra_el_pago() throws Exception {
        String llave = UUID.randomUUID().toString();

        registrar(llave, "comprobantes/B-101/" + llave + ".jpg")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("F8"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pago WHERE idempotency_key = ?::uuid", Integer.class, llave))
                .isZero();
    }

    @Test
    void F8_sin_almacen_configurado_pedir_la_subida_avisa_y_el_pago_sin_foto_se_registra() throws Exception {
        graphql(
                        token(RESIDENTE, "RESIDENTE"),
                        "mutation { solicitarSubidaComprobante(unidadId: \"" + unidad("A-101") + "\", idempotencyKey: \""
                                + UUID.randomUUID() + "\", tipoContenido: \"image/jpeg\") { url } }")
                .andExpect(jsonPath("$.errors[0].extensions.code").value("ALMACEN_NO_DISPONIBLE"));

        String llave = UUID.randomUUID().toString();
        String ayer = LocalDate.now().minusDays(1).toString();
        graphql(
                        token(RESIDENTE, "RESIDENTE"),
                        "mutation { registrarPago(unidadId: \"" + unidad("A-101") + "\", monto: \"10.00\", fechaPago: \"" + ayer
                                + "\", banco: \"BNB\", idempotencyKey: \"" + llave + "\") { estado } }")
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
