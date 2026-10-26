package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.ImputacionDePago;
import com.condohub.gestion.domain.model.SaldoAFavor;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saldo a favor de los pagos confirmados y su aplicación a cargos (F7). Las lecturas bloquean las filas. */
public interface SaldoAFavorRepository {

    /** Por unidad, los pagos confirmados con saldo sin imputar, del más antiguo al más reciente. */
    Map<UUID, List<SaldoAFavor>> saldosAFavorPorUnidad();

    /** Como {@link #saldosAFavorPorUnidad()}, pero solo de una unidad (no lee ni bloquea los pagos de las demás). */
    default List<SaldoAFavor> saldosAFavorDe(UUID unidadId) {
        return saldosAFavorPorUnidad().getOrDefault(unidadId, List.of());
    }

    List<CargoPendiente> cargosPendientes(UUID unidadId);

    /** Guarda las imputaciones y baja el saldo de cada cargo (PAGADO al llegar a 0). */
    void imputar(List<ImputacionDePago> imputaciones);
}
