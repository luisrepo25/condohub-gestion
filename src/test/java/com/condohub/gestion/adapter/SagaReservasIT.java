package com.condohub.gestion.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Saga de reserva, paso 2, de punta a punta con PostgreSQL y RabbitMQ reales: Reservas manda el comando a la cola
 * {@code finanzas.reservas}, Gestión crea o anula los cargos y responde por su outbox (R5–R7, F6, F7, S4, S5).
 */
@Testcontainers
@SpringBootTest(properties = {
    "condohub.outbox.relay.habilitado=true",
    "condohub.outbox.intervalo-ms=300",
    "condohub.saga.consumidor.habilitado=true",
    "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SagaReservasIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    @Container
    @ServiceConnection
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    private static final String RESPUESTAS = "prueba.respuestas-saga";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired TopicExchange eventos;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void colaDeRespuestas() {
        var cola = new Queue(RESPUESTAS, false);
        admin.declareQueue(cola);
        // En un topic, `*` reemplaza una palabra entera: se enlazan las dos respuestas por su nombre exacto.
        admin.declareBinding(BindingBuilder.bind(cola).to(eventos).with("finanzas.CargoAplicado"));
        admin.declareBinding(BindingBuilder.bind(cola).to(eventos).with("finanzas.CargoAnulado"));
        admin.purgeQueue(RESPUESTAS);
    }

    /** Una unidad del demo sin cargos pendientes, para que el saldo a favor sea exacto. */
    private UUID unidadAlDia(int indice) {
        return jdbc.queryForList(
                        """
                        SELECT u.id FROM unidad u
                         WHERE NOT EXISTS (SELECT 1 FROM cargo c WHERE c.unidad_id = u.id AND c.estado = 'PENDIENTE')
                         ORDER BY u.codigo
                        """,
                        UUID.class)
                .get(indice);
    }

    private UUID enviar(String tipo, UUID sagaId, Map<String, Object> data) throws Exception {
        UUID eventId = UUID.randomUUID();
        enviar(eventId, tipo, sagaId, data);
        return eventId;
    }

    private void enviar(UUID eventId, String tipo, UUID sagaId, Map<String, Object> data) throws Exception {
        Map<String, Object> sobre = Map.of(
                "eventId", eventId.toString(), "type", tipo, "occurredAt", Instant.now().toString(),
                "sagaId", sagaId.toString(), "version", 1, "data", data);
        Message mensaje = MessageBuilder.withBody(JSON.writeValueAsBytes(sobre)).setContentType("application/json").build();
        rabbit.send(eventos.getName(), tipo, mensaje);
    }

    private JsonNode respuesta() throws Exception {
        Message mensaje = rabbit.receive(RESPUESTAS, 15_000);
        assertThat(mensaje).as("Gestión debió responder a la saga").isNotNull();
        return JSON.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
    }

    private static Map<String, Object> aplicar(UUID reserva, UUID unidad, String area, String uso, String garantia) {
        return Map.of("reservaId", reserva.toString(), "unidadId", unidad.toString(), "areaComun", area, "costoUso", uso, "garantia", garantia);
    }

    private static Map<String, Object> anular(UUID reserva, UUID unidad, String alcance) {
        return Map.of("reservaId", reserva.toString(), "unidadId", unidad.toString(), "alcance", alcance);
    }

    private List<Map<String, Object>> cargosDe(UUID reserva) {
        return jdbc.queryForList(
                "SELECT id, tipo::text AS tipo, estado::text AS estado, monto, saldo, fecha_vencimiento FROM cargo WHERE referencia_externa = ? ORDER BY tipo",
                reserva.toString());
    }

    @Test
    void R5_S4_aplicar_cargos_del_salon_crea_uso_y_garantia_y_responde_CargoAplicado_con_el_sagaId() throws Exception {
        UUID reserva = UUID.randomUUID(), unidad = unidadAlDia(0), saga = UUID.randomUUID();

        enviar("reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));

        JsonNode r = respuesta();
        assertThat(r.path("type").asText()).isEqualTo("finanzas.CargoAplicado");
        assertThat(r.path("sagaId").asText()).isEqualTo(saga.toString());
        assertThat(r.at("/data/reservaId").asText()).isEqualTo(reserva.toString());
        var cargos = cargosDe(reserva);
        assertThat(cargos).extracting(c -> c.get("tipo")).containsExactly("GARANTIA", "USO_AREA");
        assertThat(cargos).extracting(c -> c.get("estado")).containsOnly("PENDIENTE");
        assertThat(cargos).extracting(c -> ((BigDecimal) c.get("monto")).toPlainString()).containsExactly("300.00", "500.00");
        var vence = LocalDate.now(ZoneOffset.UTC).plusMonths(1).withDayOfMonth(10);
        assertThat(cargos).extracting(c -> c.get("fecha_vencimiento").toString()).containsOnly(vence.toString());
        assertThat(r.at("/data/cargoIds")).hasSize(2);
    }

    @Test
    void S5_el_mismo_comando_dos_veces_no_crea_cargos_ni_responde_otra_vez() throws Exception {
        UUID reserva = UUID.randomUUID(), unidad = unidadAlDia(1), saga = UUID.randomUUID(), eventId = UUID.randomUUID();

        enviar(eventId, "reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));
        respuesta();
        enviar(eventId, "reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));

        assertThat(rabbit.receive(RESPUESTAS, 2_000)).as("un repetido no genera otra respuesta").isNull();
        assertThat(cargosDe(reserva)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_procesado WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void S5_otro_comando_para_la_misma_reserva_no_duplica_sus_cargos() throws Exception {
        UUID reserva = UUID.randomUUID(), unidad = unidadAlDia(2), saga = UUID.randomUUID();

        enviar("reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));
        JsonNode primera = respuesta();
        enviar("reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));
        JsonNode segunda = respuesta();

        assertThat(cargosDe(reserva)).hasSize(2);
        assertThat(segunda.at("/data/cargoIds")).isEqualTo(primera.at("/data/cargoIds"));
    }

    @Test
    void R5_un_area_sin_costo_responde_CargoAplicado_sin_cargos() throws Exception {
        UUID reserva = UUID.randomUUID();

        enviar("reservas.AplicarCargosReserva", UUID.randomUUID(), aplicar(reserva, unidadAlDia(3), "PISCINA", "0.00", "0.00"));

        assertThat(respuesta().at("/data/cargoIds")).isEmpty();
        assertThat(cargosDe(reserva)).isEmpty();
    }

    @Test
    void R6_R7_F6_solo_garantia_anula_la_garantia_con_nota_de_credito_y_mantiene_el_uso() throws Exception {
        UUID reserva = UUID.randomUUID(), unidad = unidadAlDia(4), saga = UUID.randomUUID();
        enviar("reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "SALON", "500.00", "300.00"));
        respuesta();

        enviar("reservas.AnularCargosReserva", saga, anular(reserva, unidad, "SOLO_GARANTIA"));

        JsonNode r = respuesta();
        assertThat(r.path("type").asText()).isEqualTo("finanzas.CargoAnulado");
        var cargos = cargosDe(reserva);
        assertThat(cargos).extracting(c -> c.get("tipo") + "=" + c.get("estado")).containsExactly("GARANTIA=ANULADO", "USO_AREA=PENDIENTE");
        assertThat(r.at("/data/cargoIds").get(0).asText()).isEqualTo(cargos.get(0).get("id").toString());
        assertThat(jdbc.queryForObject(
                        "SELECT monto FROM nota_credito WHERE cargo_id = ?", BigDecimal.class, cargos.get(0).get("id")))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void R7_F7_anular_todos_un_cargo_ya_pagado_devuelve_lo_pagado_como_saldo_a_favor() throws Exception {
        UUID reserva = UUID.randomUUID(), unidad = unidadAlDia(5), saga = UUID.randomUUID();
        enviar("reservas.AplicarCargosReserva", saga, aplicar(reserva, unidad, "CHURR-1", "50.00", "0.00"));
        respuesta();
        pagarYConfirmar(unidad, "50.00");
        assertThat(cargosDe(reserva).get(0).get("estado")).isEqualTo("PAGADO");

        enviar("reservas.AnularCargosReserva", saga, anular(reserva, unidad, "TODOS"));
        respuesta();

        UUID cargo = (UUID) cargosDe(reserva).get(0).get("id");
        assertThat(cargosDe(reserva).get(0).get("estado")).isEqualTo("ANULADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM imputacion_pago WHERE cargo_id = ?", Integer.class, cargo)).isZero();
        assertThat(jdbc.queryForObject("SELECT saldo_a_favor FROM v_estado_cuenta WHERE unidad_id = ?", BigDecimal.class, unidad))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void S2_anular_una_reserva_sin_cargos_responde_igual_con_la_lista_vacia() throws Exception {
        enviar("reservas.AnularCargosReserva", UUID.randomUUID(), anular(UUID.randomUUID(), unidadAlDia(6), "TODOS"));

        JsonNode r = respuesta();
        assertThat(r.path("type").asText()).isEqualTo("finanzas.CargoAnulado");
        assertThat(r.at("/data/cargoIds")).isEmpty();
    }

    @Test
    void un_comando_invalido_no_traba_la_cola_y_el_siguiente_se_procesa() throws Exception {
        rabbit.send(eventos.getName(), "reservas.AplicarCargosReserva", MessageBuilder.withBody("no es json".getBytes()).build());
        enviar("reservas.AplicarCargosReserva", UUID.randomUUID(), aplicar(UUID.randomUUID(), UUID.randomUUID(), "SALON", "500.00", "300.00"));
        UUID reserva = UUID.randomUUID();
        enviar("reservas.AplicarCargosReserva", UUID.randomUUID(), aplicar(reserva, unidadAlDia(7), "SALON", "500.00", "0.00"));

        // La unidad inexistente no se puede cumplir: no hay respuesta para ella; la cola sigue con la siguiente.
        assertThat(respuesta().at("/data/reservaId").asText()).isEqualTo(reserva.toString());
        assertThat(cargosDe(reserva)).hasSize(1);
    }

    private void pagarYConfirmar(UUID unidad, String monto) throws Exception {
        var admin = jwt().jwt(j -> j.subject("00000000-0000-0000-0000-00000000a001")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        String registrar = "mutation { registrarPago(unidadId: \\\"" + unidad + "\\\", monto: \\\"" + monto + "\\\", fechaPago: \\\""
                + LocalDate.now(ZoneOffset.UTC) + "\\\", idempotencyKey: \\\"" + UUID.randomUUID() + "\\\") { id } }";
        String cuerpo = mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + registrar + "\"}").with(admin))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String pagoId = JsonPath.read(cuerpo, "$.data.registrarPago.id");
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mutation { confirmarPago(pagoId: \\\"" + pagoId + "\\\") { reciboNumero } }\"}")
                        .with(admin))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
