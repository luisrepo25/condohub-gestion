package com.condohub.gestion.domain.model;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado {@code Pago} en la conciliación (F8): el ADMIN lo confirma o lo rechaza, y solo mientras está
 * {@link EstadoPago#PENDIENTE}.
 */
public record PagoPorRevisar(UUID id, UUID unidadId, Dinero monto, EstadoPago estado) {

    static final int MOTIVO_LARGO_MAXIMO = 300;

    public PagoPorRevisar {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unidadId, "unidadId");
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(estado, "estado");
    }

    /** F7, F8: al confirmar, el pago se reparte FIFO entre los cargos pendientes de la unidad. */
    public ResultadoImputacion confirmar(Collection<CargoPendiente> cargosPendientes, ImputadorPagos imputador) {
        exigirPendiente();
        return imputador.imputar(monto, cargosPendientes);
    }

    /**
     * F8: el rechazo exige motivo.
     *
     * @return el motivo normalizado que se guarda
     */
    public String rechazar(String motivo) {
        exigirPendiente();
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new ReglaNegocioVioladaException("F8", "El rechazo de un pago exige un motivo");
        }
        if (limpio.length() > MOTIVO_LARGO_MAXIMO) {
            throw new ReglaNegocioVioladaException(
                    "F8", "El motivo del rechazo admite como máximo " + MOTIVO_LARGO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    private void exigirPendiente() {
        if (estado != EstadoPago.PENDIENTE) {
            throw new ReglaNegocioVioladaException("F8", "El pago ya fue revisado: está " + estado);
        }
    }
}
