package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.CargoVencido;
import com.condohub.gestion.domain.model.RecargoCalculado;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Recargos por mora (F5). */
public interface RecargoRepository {

    boolean existeUnidad(UUID unidadId);

    /** Cargos vencidos a {@code hoy}, impagos y que no son recargos, sin propuesta en el periodo. */
    List<CargoVencido> cargosVencidosSinPropuesta(UUID unidadId, Periodo periodo, LocalDate hoy);

    /** Propuestas de la unidad en el periodo, en cualquier estado. */
    List<RecargoPropuesto> delPeriodo(UUID unidadId, Periodo periodo);

    void guardarPropuestas(
            UUID unidadId,
            Periodo periodo,
            BigDecimal porcentaje,
            List<RecargoCalculado> recargos,
            String origen,
            String misionId,
            String motivo);

    /** Recargos en estado PROPUESTO de todas las unidades. */
    List<RecargoPropuesto> pendientes();

    /** La propuesta, bloqueada hasta el fin de la transacción. */
    Optional<RecargoPropuesto> porIdParaResolver(UUID propuestaId);

    /** Saldo pendiente del cargo, bloqueado hasta el fin de la transacción; {@code Dinero.CERO} si ya no está PENDIENTE. */
    Dinero saldoPendienteDelCargo(UUID cargoId);

    /** Crea el cargo RECARGO de la propuesta, emitido {@code hoy} y con vencimiento {@code vence}; devuelve su id. */
    UUID crearCargoRecargo(RecargoPropuesto propuesta, LocalDate hoy, LocalDate vence);

    void marcarConfirmado(UUID propuestaId, String adminSub, UUID cargoId);

    void marcarRechazado(UUID propuestaId, String adminSub, String motivo);

    RecargoPropuesto porId(UUID propuestaId);
}
