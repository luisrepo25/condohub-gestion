package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import com.condohub.gestion.application.port.out.RegistroPagoRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.Pago;
import com.condohub.gestion.domain.model.RegistroDePago;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RegistrarPagoService implements RegistrarPagoUseCase {

    private final RegistroPagoRepository pagos;
    private final UnidadRepository unidades;
    private final PoliticaVisibilidad politica;
    private final Clock reloj;

    public RegistrarPagoService(
            RegistroPagoRepository pagos, UnidadRepository unidades, PoliticaVisibilidad politica, Clock reloj) {
        this.pagos = pagos;
        this.unidades = unidades;
        this.politica = politica;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Pago registrar(Solicitante solicitante, Comando comando) {
        var clave = IdempotencyKey.de(comando.idempotencyKey());

        // F1 primero: un residente solo registra pagos de su unidad (y no averigua si otra existe).
        Set<UUID> propias = solicitante.tieneRol(Rol.ADMIN) ? Set.of() : unidades.idsDeUnidadesDe(solicitante.sub());
        politica.verificarPuedeVerEstadoDeCuenta(solicitante, comando.unidadId(), propias);

        // O3: la misma clave devuelve lo mismo que la primera vez, aunque los datos cambien.
        var previo = pagos.porIdempotencyKey(clave);
        if (previo.isPresent()) {
            politica.verificarPuedeVerEstadoDeCuenta(solicitante, previo.get().unidadId(), propias);
            return previo.get();
        }

        if (!pagos.existeUnidad(comando.unidadId())) {
            throw new RecursoNoEncontradoException("No existe la unidad " + comando.unidadId());
        }
        var fechaPago = fecha(comando.fechaPago());
        RegistroDePago.exigirFechaNoFutura(fechaPago, LocalDate.now(reloj));
        var registro = new RegistroDePago(
                comando.unidadId(),
                Dinero.de(comando.monto()),
                fechaPago,
                comando.banco(),
                clave,
                solicitante.sub(),
                horaDispositivo(comando.horaDispositivo()));
        return pagos.guardar(registro);
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ReglaNegocioVioladaException("F8", "La fecha del comprobante debe ser yyyy-MM-dd: " + valor);
        }
    }

    private static Instant horaDispositivo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).toInstant();
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioVioladaException("O1", "La hora del dispositivo debe ser ISO-8601 con zona: " + valor);
        }
    }
}
