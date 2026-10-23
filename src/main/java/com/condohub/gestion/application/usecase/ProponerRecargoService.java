package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ProponerRecargoUseCase;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.RecargoRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.EstadoRecargo;
import com.condohub.gestion.domain.model.RecargoPropuesto;
import com.condohub.gestion.domain.service.CalculadoraRecargos;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Transactional;

public class ProponerRecargoService implements ProponerRecargoUseCase {

    static final String PORCENTAJE_MENSUAL = "recargo.porcentaje_mensual";

    private final RecargoRepository recargos;
    private final ParametroRepository parametros;
    private final CalculadoraRecargos calculadora;
    private final Clock reloj;

    public ProponerRecargoService(
            RecargoRepository recargos, ParametroRepository parametros, CalculadoraRecargos calculadora, Clock reloj) {
        this.recargos = recargos;
        this.parametros = parametros;
        this.calculadora = calculadora;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public List<RecargoPropuesto> proponer(Comando comando) {
        if (!recargos.existeUnidad(comando.unidadId())) {
            throw new RecursoNoEncontradoException("No existe la unidad " + comando.unidadId());
        }
        String motivo = RecargoPropuesto.motivo(comando.motivo());
        LocalDate hoy = LocalDate.now(reloj);
        Periodo periodo = Periodo.delMes(hoy);
        BigDecimal porcentaje = parametros.decimal(PORCENTAJE_MENSUAL);

        var nuevos = calculadora.calcular(
                porcentaje, recargos.cargosVencidosSinPropuesta(comando.unidadId(), periodo, hoy));
        var yaPropuestos = recargos.delPeriodo(comando.unidadId(), periodo).stream()
                .filter(r -> r.estado() == EstadoRecargo.PROPUESTO)
                .toList();
        if (nuevos.isEmpty() && yaPropuestos.isEmpty()) {
            throw new ReglaNegocioVioladaException(
                    "F5", "La unidad no tiene saldo vencido sin recargo en " + periodo);
        }
        if (comando.monto() != null) {
            var total = Stream.concat(nuevos.stream().map(r -> r.monto().monto()), yaPropuestos.stream()
                            .map(r -> r.monto().monto()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            calculadora.exigirMontoSolicitado(Dinero.de(comando.monto()), Dinero.de(total));
        }
        if (!nuevos.isEmpty()) {
            String origen = comando.misionId() == null || comando.misionId().isBlank() ? "ADMIN" : "AGENTE";
            recargos.guardarPropuestas(
                    comando.unidadId(), periodo, porcentaje, nuevos, origen, comando.misionId(), motivo);
        }
        return recargos.delPeriodo(comando.unidadId(), periodo);
    }
}
