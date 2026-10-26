package com.condohub.gestion.adapter.out.persistence;

import com.condohub.gestion.application.port.out.RecargoRepository;
import com.condohub.gestion.domain.model.CargoVencido;
import com.condohub.gestion.domain.model.EstadoRecargo;
import com.condohub.gestion.domain.model.RecargoCalculado;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RecargoPersistenceAdapter implements RecargoRepository {

    private static final String COLUMNAS = """
            id, unidad_id, cargo_origen_id, periodo, porcentaje, base, monto, estado, origen, motivo, cargo_generado_id
            """;

    private final JdbcTemplate jdbc;

    RecargoPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existeUnidad(UUID unidadId) {
        Integer cantidad = jdbc.queryForObject("SELECT count(*) FROM unidad WHERE id = ?", Integer.class, unidadId);
        return cantidad != null && cantidad > 0;
    }

    @Override
    public List<CargoVencido> cargosVencidosSinPropuesta(UUID unidadId, Periodo periodo, LocalDate hoy) {
        return jdbc.query(
                """
                SELECT c.id, c.saldo FROM cargo c
                 WHERE c.unidad_id = ? AND c.estado = 'PENDIENTE' AND c.tipo <> 'RECARGO'
                   AND c.fecha_vencimiento < ?
                   AND NOT EXISTS (SELECT 1 FROM recargo_propuesto r
                                    WHERE r.cargo_origen_id = c.id AND r.periodo = ?)
                 ORDER BY c.fecha_vencimiento, c.creado_en
                """,
                (rs, i) -> new CargoVencido(
                        rs.getObject("id", UUID.class), Dinero.de(rs.getObject("saldo", BigDecimal.class))),
                unidadId,
                hoy,
                periodo.primerDia());
    }

    @Override
    public List<RecargoPropuesto> delPeriodo(UUID unidadId, Periodo periodo) {
        return jdbc.query(
                "SELECT " + COLUMNAS + " FROM recargo_propuesto WHERE unidad_id = ? AND periodo = ? ORDER BY propuesto_en, id",
                (rs, i) -> recargo(rs),
                unidadId,
                periodo.primerDia());
    }

    @Override
    public void guardarPropuestas(
            UUID unidadId,
            Periodo periodo,
            BigDecimal porcentaje,
            List<RecargoCalculado> recargos,
            String origen,
            String misionId,
            String motivo) {
        // ON CONFLICT: dos propuestas simultáneas del mismo cargo y mes no se duplican (UNIQUE cargo_origen_id, periodo).
        jdbc.batchUpdate(
                """
                INSERT INTO recargo_propuesto (unidad_id, cargo_origen_id, periodo, porcentaje, base, monto, origen,
                                               mision_id, motivo)
                VALUES (?, ?, ?, ?, ?, ?, ?::origen_propuesta, ?, ?)
                ON CONFLICT (cargo_origen_id, periodo) DO NOTHING
                """,
                recargos.stream()
                        .map(r -> new Object[] {
                            unidadId,
                            r.cargoOrigenId(),
                            periodo.primerDia(),
                            porcentaje,
                            r.base().monto(),
                            r.monto().monto(),
                            origen,
                            misionId,
                            motivo
                        })
                        .toList());
    }

    @Override
    public List<RecargoPropuesto> pendientes() {
        return jdbc.query(
                "SELECT " + COLUMNAS + " FROM recargo_propuesto WHERE estado = 'PROPUESTO' ORDER BY propuesto_en, id",
                (rs, i) -> recargo(rs));
    }

    @Override
    public Optional<RecargoPropuesto> porIdParaResolver(UUID propuestaId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM recargo_propuesto WHERE id = ? FOR UPDATE", (rs, i) -> recargo(rs), propuestaId)
                .stream()
                .findFirst();
    }

    @Override
    public Dinero saldoPendienteDelCargo(UUID cargoId) {
        // FOR UPDATE: un pago que se confirme a la vez espera a que termine esta transacción (y al revés).
        return jdbc.query(
                        "SELECT saldo FROM cargo WHERE id = ? AND estado = 'PENDIENTE' FOR UPDATE",
                        (rs, i) -> Dinero.de(rs.getObject("saldo", BigDecimal.class)),
                        cargoId)
                .stream()
                .findFirst()
                .orElse(Dinero.CERO);
    }

    @Override
    public UUID crearCargoRecargo(RecargoPropuesto p, LocalDate hoy, LocalDate vence) {
        return jdbc.queryForObject(
                """
                INSERT INTO cargo (unidad_id, tipo, concepto, monto, saldo, fecha_emision, fecha_vencimiento,
                                   cargo_origen_id)
                SELECT ?, 'RECARGO', left('Recargo por mora ' || ? || ' % ' || ? || ' - ' || o.concepto, 200),
                       ?, ?, ?, ?, o.id
                  FROM cargo o WHERE o.id = ?
                RETURNING id
                """,
                UUID.class,
                p.unidadId(),
                p.porcentaje().toPlainString(),
                p.periodo().toString(),
                p.monto().monto(),
                p.monto().monto(),
                hoy,
                vence,
                p.cargoOrigenId());
    }

    @Override
    public void marcarConfirmado(UUID propuestaId, String adminSub, UUID cargoId) {
        jdbc.update(
                """
                UPDATE recargo_propuesto SET estado = 'CONFIRMADO', resuelto_por = ?, resuelto_en = now(),
                       cargo_generado_id = ?
                 WHERE id = ? AND estado = 'PROPUESTO'
                """,
                adminSub,
                cargoId,
                propuestaId);
    }

    @Override
    public void marcarRechazado(UUID propuestaId, String adminSub, String motivo) {
        // La tabla tiene un solo motivo: el del rechazo se agrega al de la propuesta.
        jdbc.update(
                """
                UPDATE recargo_propuesto SET estado = 'RECHAZADO', resuelto_por = ?, resuelto_en = now(),
                       motivo = CASE WHEN ?::text IS NULL THEN motivo
                                     ELSE left(concat_ws(' | ', motivo, 'Rechazo: ' || ?), 300) END
                 WHERE id = ? AND estado = 'PROPUESTO'
                """,
                adminSub,
                motivo,
                motivo,
                propuestaId);
    }

    @Override
    public RecargoPropuesto porId(UUID propuestaId) {
        return jdbc.queryForObject(
                "SELECT " + COLUMNAS + " FROM recargo_propuesto WHERE id = ?", (rs, i) -> recargo(rs), propuestaId);
    }

    private static RecargoPropuesto recargo(ResultSet rs) throws SQLException {
        return new RecargoPropuesto(
                rs.getObject("id", UUID.class),
                rs.getObject("unidad_id", UUID.class),
                rs.getObject("cargo_origen_id", UUID.class),
                Periodo.delMes(rs.getObject("periodo", LocalDate.class)),
                rs.getObject("porcentaje", BigDecimal.class),
                Dinero.de(rs.getObject("base", BigDecimal.class)),
                Dinero.de(rs.getObject("monto", BigDecimal.class)),
                EstadoRecargo.valueOf(rs.getString("estado")),
                rs.getString("origen"),
                rs.getString("motivo"),
                rs.getObject("cargo_generado_id", UUID.class));
    }
}
