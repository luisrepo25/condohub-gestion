package com.condohub.gestion.domain.valueobject;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import java.util.Objects;
import java.util.UUID;

/** Clave de idempotencia de una operación creada en una PWA: UUID v4 generado una sola vez en el dispositivo (O1, O3). */
public record IdempotencyKey(UUID valor) {

    public IdempotencyKey {
        Objects.requireNonNull(valor, "valor");
        if (valor.version() != 4) {
            throw new ReglaNegocioVioladaException("O1", "La Idempotency-Key debe ser un UUID v4: " + valor);
        }
    }

    public static IdempotencyKey de(String valor) {
        try {
            return new IdempotencyKey(UUID.fromString(valor));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ReglaNegocioVioladaException("O1", "La Idempotency-Key debe ser un UUID v4: " + valor);
        }
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
