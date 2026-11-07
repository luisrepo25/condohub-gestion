package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.ComprobantePagoRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Guarda y lee la clave de la foto del comprobante en {@code pago.comprobante_key} (F8). */
@Component
public class ComprobantePagoPersistenceAdapter implements ComprobantePagoRepository {

    private final JdbcTemplate jdbc;

    ComprobantePagoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ComprobanteDePago> porPago(UUID pagoId) {
        return jdbc
                .query(
                        "SELECT unidad_id, comprobante_key FROM pago WHERE id = ?",
                        (rs, i) -> new ComprobanteDePago(rs.getObject("unidad_id", UUID.class), rs.getString("comprobante_key")),
                        pagoId)
                .stream()
                .findFirst();
    }

    @Override
    public void enlazar(UUID pagoId, String clave) {
        jdbc.update("UPDATE pago SET comprobante_key = ? WHERE id = ? AND comprobante_key IS NULL", clave, pagoId);
    }
}
