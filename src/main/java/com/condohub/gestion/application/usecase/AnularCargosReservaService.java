package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.AnularCargosReservaUseCase;
import com.condohub.gestion.application.port.out.CargoReservaRepository;
import com.condohub.gestion.application.port.out.RespuestasSaga;
import com.condohub.gestion.domain.model.AlcanceAnulacion;
import com.condohub.gestion.domain.model.CargoAplicadoDeReserva;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensación del paso 2 y cancelaciones (R6, R7, F6): anula con nota de crédito los cargos de la reserva que indica el
 * alcance (nunca se borran). Lo que la unidad ya hubiera pagado de ellos vuelve como saldo a favor y se aplica a sus
 * otros cargos pendientes (F7). Responde {@code finanzas.CargoAnulado} por el outbox, en la misma transacción (S4).
 * Si los cargos no existen (la saga falló antes de crearlos) responde igual, con la lista vacía.
 */
public class AnularCargosReservaService implements AnularCargosReservaUseCase {

    static final String TIPO = "reservas.AnularCargosReserva";

    private final CargoReservaRepository cargos;
    private final RespuestasSaga respuestas;
    private final AplicarSaldoAFavor saldoAFavor;

    public AnularCargosReservaService(CargoReservaRepository cargos, RespuestasSaga respuestas, AplicarSaldoAFavor saldoAFavor) {
        this.cargos = cargos;
        this.respuestas = respuestas;
        this.saldoAFavor = saldoAFavor;
    }

    @Override
    @Transactional
    public Resultado anular(Comando c) {
        if (!cargos.marcarProcesado(c.eventId(), TIPO)) {
            return new Resultado(false, List.of());
        }
        List<UUID> anulados = new ArrayList<>();
        BigDecimal devuelto = BigDecimal.ZERO;
        for (CargoAplicadoDeReserva cargo : cargos.deLaReserva(c.reservaId())) {
            if (cargo.anulado() || !c.alcance().incluye(cargo.tipo())) {
                continue;
            }
            String motivo = "Reserva " + c.reservaId() + ": " + (c.alcance() == AlcanceAnulacion.TODOS
                    ? "se anulan sus cargos (R7)"
                    : "se devuelve la garantía (R6/R7)");
            Dinero pagado = cargos.anular(cargo.id(), motivo, c.reservaId() + ":" + cargo.tipo());
            devuelto = devuelto.add(pagado.monto());
            anulados.add(cargo.id());
        }
        if (devuelto.signum() > 0) {
            // F7: lo que se había pagado de los cargos anulados paga otros cargos pendientes de la unidad.
            saldoAFavor.aplicar(c.unidadId());
        }
        respuestas.cargoAnulado(c.eventId(), c.sagaId(), c.reservaId(), c.unidadId(), anulados);
        return new Resultado(true, List.copyOf(anulados));
    }
}
