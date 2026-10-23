package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.PeriodoExpensasRepository;
import com.condohub.gestion.domain.model.PeriodoExpensas;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PeriodoExpensasPersistenceAdapter implements PeriodoExpensasRepository {

    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final JdbcTemplate jdbc;

    PeriodoExpensasPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existe(Periodo periodo) {
        Integer cantidad = jdbc.queryForObject(
                "SELECT count(*) FROM periodo_expensas WHERE periodo = ?", Integer.class, periodo.primerDia());
        return cantidad != null && cantidad > 0;
    }

    @Override
    public void guardar(PeriodoExpensas p) {
        UUID periodoId = jdbc.queryForObject(
                """
                INSERT INTO periodo_expensas (periodo, presupuesto, estado, fecha_vencimiento, emitido_por, emitido_en)
                VALUES (?, ?, 'EMITIDO', ?, ?, now())
                RETURNING id
                """,
                UUID.class,
                p.periodo().primerDia(),
                p.presupuesto().monto(),
                p.fechaVencimiento(),
                p.emitidoPor());

        String concepto = "Expensa " + p.periodo().primerDia().format(MES_ANIO);
        List<Object[]> cargos = new ArrayList<>();
        p.expensas().forEach((unidadId, monto) -> cargos.add(new Object[] {
            unidadId, concepto, monto.monto(), monto.monto(), p.periodo().primerDia(), p.fechaVencimiento(), periodoId
        }));
        jdbc.batchUpdate(
                """
                INSERT INTO cargo (unidad_id, tipo, concepto, monto, saldo, fecha_emision, fecha_vencimiento, periodo_id)
                VALUES (?, 'EXPENSA', ?, ?, ?, ?, ?, ?)
                """,
                cargos);
    }
}
