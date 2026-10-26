package com.condohub.gestion.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.condohub.gestion.domain.event.EventoFinanzas;
import com.condohub.gestion.domain.event.PagoConfirmado;
import com.condohub.gestion.domain.event.PagoRegistrado;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Los eventos que publica Gestión cumplen su contrato de {@code contracts/events} (sobre común + data). Fuera del
 * espacio de trabajo completo (sin la carpeta contracts) la prueba se omite sola.
 */
class ContratoEventosTest {

    private static final Path CONTRATOS = Path.of("../../contracts/events");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final UUID pagoId = UUID.randomUUID();
    private final UUID unidadId = UUID.randomUUID();

    @Test
    void S4_PagoRegistrado_cumple_su_contrato() throws Exception {
        validar(new PagoRegistrado(pagoId, unidadId, Dinero.de("390"), LocalDate.of(2026, 10, 20), "Banco Unión"));
        validar(new PagoRegistrado(pagoId, unidadId, Dinero.de("0.5"), LocalDate.of(2026, 10, 20), null));
    }

    @Test
    void S4_PagoConfirmado_cumple_su_contrato() throws Exception {
        validar(new PagoConfirmado(pagoId, unidadId, Dinero.de("390.00"), 123));
    }

    @Test
    void S5_el_mismo_evento_del_mismo_pago_tiene_siempre_el_mismo_eventId() {
        var uno = new PagoRegistrado(pagoId, unidadId, Dinero.de("1"), LocalDate.now(), null);
        var otro = new PagoRegistrado(pagoId, unidadId, Dinero.de("2"), LocalDate.now(), "x");

        assertThat(SobreEvento.eventId(uno)).isEqualTo(SobreEvento.eventId(otro));
        assertThat(SobreEvento.eventId(uno))
                .isNotEqualTo(SobreEvento.eventId(new PagoConfirmado(pagoId, unidadId, Dinero.de("1"), 1)));
    }

    @Test
    void S4_cada_evento_indica_el_agregado_que_lo_produjo() {
        var registrado = new PagoRegistrado(pagoId, unidadId, Dinero.de("1"), LocalDate.now(), null);
        var confirmado = new PagoConfirmado(pagoId, unidadId, Dinero.de("1"), 1);

        assertThat(registrado.tipoAgregado()).isEqualTo("Pago");
        assertThat(confirmado.tipoAgregado()).isEqualTo("Pago");
        assertThat(confirmado.agregadoId()).isEqualTo(pagoId);
    }

    private void validar(EventoFinanzas evento) throws Exception {
        assumeTrue(Files.isDirectory(CONTRATOS), "sin contracts/events: prueba de contrato omitida");
        String mensaje = SobreEvento.sobre(
                SobreEvento.eventId(evento), evento.tipo(), Instant.now(), null, SobreEvento.datosJson(evento));

        var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        var esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(
                        SchemaLocation.of(CONTRATOS.resolve(evento.tipo() + ".schema.json").toUri().toString()),
                        config);
        var errores = esquema.validate(JSON.readTree(mensaje));

        assertThat(errores).as(mensaje).isEmpty();
    }
}
