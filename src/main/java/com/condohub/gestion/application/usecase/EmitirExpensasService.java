package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.EmitirExpensasUseCase;
import com.condohub.gestion.application.port.out.AlicuotaRepository;
import com.condohub.gestion.application.port.out.ParametroRepository;
import com.condohub.gestion.application.port.out.PeriodoExpensasRepository;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.PeriodoExpensas;
import com.condohub.gestion.domain.service.CalculadoraExpensas;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import org.springframework.transaction.annotation.Transactional;

public class EmitirExpensasService implements EmitirExpensasUseCase {

    static final String DIA_VENCIMIENTO = "expensas.dia_vencimiento";

    private final PeriodoExpensasRepository periodos;
    private final AlicuotaRepository alicuotas;
    private final ParametroRepository parametros;
    private final CalculadoraExpensas calculadora;

    public EmitirExpensasService(
            PeriodoExpensasRepository periodos,
            AlicuotaRepository alicuotas,
            ParametroRepository parametros,
            CalculadoraExpensas calculadora) {
        this.periodos = periodos;
        this.alicuotas = alicuotas;
        this.parametros = parametros;
        this.calculadora = calculadora;
    }

    @Override
    @Transactional
    public PeriodoExpensas emitir(String adminSub, Periodo periodo, Dinero presupuesto) {
        if (periodos.existe(periodo)) {
            throw new ReglaNegocioVioladaException("F3", "Las expensas de " + periodo + " ya fueron emitidas");
        }
        // TODO(regla): F3 no dice si se puede emitir un periodo pasado (sus expensas nacerían vencidas).
        var emitido = PeriodoExpensas.emitir(
                periodo,
                presupuesto,
                alicuotas.deUnidadesActivas(),
                parametros.entero(DIA_VENCIMIENTO),
                adminSub,
                calculadora);
        periodos.guardar(emitido);
        return emitido;
    }
}
