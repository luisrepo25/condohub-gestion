package com.condohub.gestion.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Las respuestas de Gestión a la saga cumplen su contrato de {@code contracts/events} (sobre + data). */
class ContratoRespuestasSagaTest {

    private static final Path CONTRATOS = Path.of("../../contracts/events");
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"finanzas.CargoAplicado", "finanzas.CargoAnulado"})
    void S4_la_respuesta_a_la_saga_cumple_su_contrato(String tipo) throws Exception {
        assumeTrue(Files.isDirectory(CONTRATOS), "sin contracts/events: prueba de contrato omitida");
        UUID reserva = UUID.randomUUID();
        for (List<UUID> cargos : List.of(List.of(UUID.randomUUID(), UUID.randomUUID()), List.<UUID>of())) {
            String mensaje = SobreEvento.sobre(
                    RespuestasSagaOutbox.eventIdDeRespuesta(tipo, UUID.randomUUID()),
                    tipo,
                    Instant.now(),
                    UUID.randomUUID(),
                    RespuestasSagaOutbox.datosJson(reserva, UUID.randomUUID(), cargos));

            var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
            var esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(SchemaLocation.of(CONTRATOS.resolve(tipo + ".schema.json").toUri().toString()), config);

            assertThat(esquema.validate(JSON.readTree(mensaje))).as(mensaje).isEmpty();
        }
    }

    @Test
    void S5_la_respuesta_a_un_mismo_comando_tiene_siempre_el_mismo_eventId() {
        UUID comando = UUID.randomUUID();

        assertThat(RespuestasSagaOutbox.eventIdDeRespuesta("finanzas.CargoAplicado", comando))
                .isEqualTo(RespuestasSagaOutbox.eventIdDeRespuesta("finanzas.CargoAplicado", comando))
                .isNotEqualTo(RespuestasSagaOutbox.eventIdDeRespuesta("finanzas.CargoAnulado", comando));
    }
}
