package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * S4 de punta a punta: registrar y confirmar un pago deja el evento en el outbox, y el relay lo entrega a RabbitMQ
 * con el sobre del contrato. Un reenvío con la misma Idempotency-Key no genera otro mensaje (O3, S5).
 */
@Testcontainers
@SpringBootTest(properties = {"condohub.outbox.relay.habilitado=true", "condohub.outbox.intervalo-ms=300"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OutboxRabbitIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    private static final String COLA = "prueba.finanzas";
    private static final String ADMIN = "00000000-0000-0000-0000-00000000a001";
    private static final String RESIDENTE = "00000000-0000-0000-0000-00000000a002";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired TopicExchange eventos;

    @BeforeEach
    void colaDePrueba() {
        var cola = new Queue(COLA, false);
        admin.declareQueue(cola);
        admin.declareBinding(BindingBuilder.bind(cola).to(eventos).with("finanzas.#"));
        admin.purgeQueue(COLA);
    }

    private static RequestPostProcessor token(String sub, String rol) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private ResultActions graphql(RequestPostProcessor auth, String consulta) throws Exception {
        return mvc.perform(post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + consulta.replace("\"", "\\\"") + "\"}")
                .with(auth));
    }

    private JsonNode recibir() throws Exception {
        Message mensaje = rabbit.receive(COLA, 10_000);
        assertThat(mensaje).as("el relay debió entregar el evento a RabbitMQ").isNotNull();
        assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
        return JSON.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void S4_registrar_y_confirmar_un_pago_publica_sus_eventos_en_rabbitmq() throws Exception {
        var unidad = jdbc.queryForObject("SELECT id::text FROM unidad WHERE codigo = 'A-101'", String.class);
        var clave = UUID.randomUUID().toString();
        var registrar = "mutation { registrarPago(unidadId: \"" + unidad + "\", monto: \"390.00\", fechaPago: \"2026-10-20\","
                + " banco: \"Banco Unión\", idempotencyKey: \"" + clave + "\") { id } }";

        var respuesta = graphql(token(RESIDENTE, "RESIDENTE"), registrar)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String pagoId = JsonPath.read(respuesta, "$.data.registrarPago.id");

        var registrado = recibir();
        assertThat(registrado.get("type").asText()).isEqualTo("finanzas.PagoRegistrado");
        assertThat(registrado.get("version").asInt()).isEqualTo(1);
        assertThat(registrado.at("/data/pagoId").asText()).isEqualTo(pagoId);
        assertThat(registrado.at("/data/monto").asText()).isEqualTo("390.00");
        assertThat(registrado.at("/data/banco").asText()).isEqualTo("Banco Unión");

        // O3 + S5: reenviar el mismo registro no genera otro mensaje.
        graphql(token(RESIDENTE, "RESIDENTE"), registrar).andExpect(jsonPath("$.data.registrarPago.id").value(pagoId));

        graphql(token(ADMIN, "ADMIN"), "mutation { confirmarPago(pagoId: \"" + pagoId + "\") { reciboNumero } }")
                .andExpect(jsonPath("$.errors").doesNotExist());

        var confirmado = recibir();
        assertThat(confirmado.get("type").asText()).isEqualTo("finanzas.PagoConfirmado");
        assertThat(confirmado.at("/data/pagoId").asText()).isEqualTo(pagoId);
        assertThat(confirmado.at("/data/reciboNumero").asText()).matches("\\d{6}");
        assertThat(rabbit.receive(COLA, 1_500)).as("no debe haber un PagoRegistrado repetido").isNull();

        var pendientes = jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_id = ? AND published_at IS NULL", Integer.class, pagoId);
        var total = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_id = ?", Integer.class, pagoId);
        assertThat(pendientes).isZero();
        assertThat(total).isEqualTo(2);
    }
}
