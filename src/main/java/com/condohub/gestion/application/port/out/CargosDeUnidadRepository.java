package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.valueobject.Dinero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lectura de los cargos pendientes de una unidad con su detalle, para mostrar cómo se aplicaría un pago (F7). */
public interface CargosDeUnidadRepository {

    /** Cargos con saldo por pagar, del vencimiento más antiguo al más reciente. No bloquea filas: es solo lectura. */
    List<CargoDeUnidad> pendientes(UUID unidadId);

    boolean existeUnidad(UUID unidadId);

    /** Un cargo pendiente con lo que se muestra al usuario (tipo y concepto). */
    record CargoDeUnidad(
            UUID id, String tipo, String concepto, Dinero saldo, LocalDate fechaVencimiento, Instant creadoEn) {}
}
