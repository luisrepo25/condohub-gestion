package com.condohub.gestion.adapter.in.graphql;

import com.condohub.gestion.application.port.in.EmitirExpensasUseCase;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.math.BigDecimal;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/** Resolver de {@code emitirExpensas}. Sin lógica de negocio: delega en el caso de uso. */
@Controller
class ExpensasMutationController {

    private final EmitirExpensasUseCase emitirExpensas;

    ExpensasMutationController(EmitirExpensasUseCase emitirExpensas) {
        this.emitirExpensas = emitirExpensas;
    }

    record EmisionExpensasDto(
            String periodo, BigDecimal presupuesto, String fechaVencimiento, int unidades, BigDecimal total) {}

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    EmisionExpensasDto emitirExpensas(
            @Argument String periodo, @Argument BigDecimal presupuesto, Authentication autenticacion) {
        var emitido = emitirExpensas.emitir(autenticacion.getName(), Periodo.de(periodo), Dinero.de(presupuesto));
        return new EmisionExpensasDto(
                emitido.periodo().toString(),
                emitido.presupuesto().monto(),
                emitido.fechaVencimiento().toString(),
                emitido.expensas().size(),
                emitido.total().monto());
    }
}
