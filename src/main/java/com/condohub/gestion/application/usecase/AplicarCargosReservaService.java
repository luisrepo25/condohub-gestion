package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.AplicarCargosReservaUseCase;
import com.condohub.gestion.application.port.out.CargoReservaRepository;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.RespuestasSaga;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.model.CargoDeReserva;
import com.condohub.gestion.domain.service.CargosDeReserva;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga de reserva, paso 2 (R5): crea los cargos de la reserva (uso del área y, en el salón, garantía), aplica el saldo
 * a favor de la unidad (F7) y deja {@code finanzas.CargoAplicado} en el outbox, todo en una transacción (S4).
 * Idempotente dos veces: por el {@code eventId} del comando (S5) y por la reserva (un cargo de cada tipo por reserva).
 */
public class AplicarCargosReservaService implements AplicarCargosReservaUseCase {

    static final String TIPO = "reservas.AplicarCargosReserva";
    static final String DIA_VENCIMIENTO = "expensas.dia_vencimiento";

    private final CargoReservaRepository cargos;
    private final ParametroRepository parametros;
    private final RespuestasSaga respuestas;
    private final AplicarSaldoAFavor saldoAFavor;
    private final CargosDeReserva cargosDeReserva;
    private final Clock reloj;

    public AplicarCargosReservaService(
            CargoReservaRepository cargos,
            ParametroRepository parametros,
            RespuestasSaga respuestas,
            AplicarSaldoAFavor saldoAFavor,
            CargosDeReserva cargosDeReserva,
            Clock reloj) {
        this.cargos = cargos;
        this.parametros = parametros;
        this.respuestas = respuestas;
        this.saldoAFavor = saldoAFavor;
        this.cargosDeReserva = cargosDeReserva;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public Resultado aplicar(Comando c) {
        if (!cargos.marcarProcesado(c.eventId(), TIPO)) {
            return new Resultado(false, List.of());
        }
        if (!cargos.existeUnidad(c.unidadId())) {
            // Un comando con una unidad que Finanzas no conoce no se puede cumplir: no se responde y la saga compensa.
            throw new RecursoNoEncontradoException("La unidad " + c.unidadId() + " no existe");
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<UUID> ids = new ArrayList<>();
        for (CargoDeReserva cargo : cargosDeReserva.para(
                c.areaComun(), c.costoUso(), c.garantia(), hoy, parametros.entero(DIA_VENCIMIENTO))) {
            ids.add(cargos.crear(c.unidadId(), c.reservaId(), cargo, hoy));
        }
        if (!ids.isEmpty()) {
            // F7: si la unidad tiene saldo a favor, paga los cargos recién creados.
            saldoAFavor.aplicar(c.unidadId());
        }
        respuestas.cargoAplicado(c.eventId(), c.sagaId(), c.reservaId(), c.unidadId(), ids);
        return new Resultado(true, List.copyOf(ids));
    }
}
