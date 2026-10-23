package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ResolverRecargoUseCase;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.RecargoRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ResolverRecargoService implements ResolverRecargoUseCase {

    static final String DIA_VENCIMIENTO = "expensas.dia_vencimiento";

    private final RecargoRepository recargos;
    private final ParametroRepository parametros;
    private final AplicarSaldoAFavor saldoAFavor;
    private final Clock reloj;

    public ResolverRecargoService(
            RecargoRepository recargos, ParametroRepository parametros, AplicarSaldoAFavor saldoAFavor, Clock reloj) {
        this.recargos = recargos;
        this.parametros = parametros;
        this.saldoAFavor = saldoAFavor;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecargoPropuesto> pendientes() {
        return recargos.pendientes();
    }

    @Override
    @Transactional
    public RecargoPropuesto confirmar(String adminSub, UUID propuestaId) {
        var propuesta = buscar(propuestaId);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate vence = propuesta.confirmar(hoy, parametros.entero(DIA_VENCIMIENTO));
        UUID cargoId = recargos.crearCargoRecargo(propuesta, hoy, vence);
        recargos.marcarConfirmado(propuestaId, adminSub, cargoId);
        // F7: si la unidad tiene saldo a favor, paga el recargo recién creado.
        saldoAFavor.aplicar();
        return recargos.porId(propuestaId);
    }

    @Override
    @Transactional
    public RecargoPropuesto rechazar(String adminSub, UUID propuestaId, String motivo) {
        var propuesta = buscar(propuestaId);
        recargos.marcarRechazado(propuestaId, adminSub, propuesta.rechazar(motivo));
        return recargos.porId(propuestaId);
    }

    private RecargoPropuesto buscar(UUID propuestaId) {
        return recargos.porIdParaResolver(propuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el recargo propuesto " + propuestaId));
    }
}
