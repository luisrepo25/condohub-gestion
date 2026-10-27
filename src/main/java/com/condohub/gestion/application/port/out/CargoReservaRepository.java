package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.CargoAplicadoDeReserva;
import com.condohub.gestion.domain.model.CargoDeReserva;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cargos que crea y anula la saga de reserva (R5–R7). Todo se usa dentro de la transacción del caso de uso. */
public interface CargoReservaRepository {

    /** S5: registra el {@code eventId} procesado; false si ya estaba (mensaje repetido). */
    boolean marcarProcesado(UUID eventId, String tipo);

    boolean existeUnidad(UUID unidadId);

    /** Cargos ya creados para la reserva, bloqueados hasta el fin de la transacción. */
    List<CargoAplicadoDeReserva> deLaReserva(UUID reservaId);

    /** Crea el cargo de la reserva; si ya existía uno de ese tipo para la reserva, no crea otro y devuelve su id. */
    UUID crear(UUID unidadId, UUID reservaId, CargoDeReserva cargo, LocalDate hoy);

    /**
     * F6: anula el cargo con una nota de crédito por su monto (nunca se borra). Si estaba pagado en todo o en parte, lo
     * pagado vuelve a la unidad como saldo a favor (F7).
     *
     * @param referencia idempotencia de la nota de crédito
     * @return lo que estaba pagado y vuelve como saldo a favor
     */
    Dinero anular(UUID cargoId, String motivo, String referencia);
}
