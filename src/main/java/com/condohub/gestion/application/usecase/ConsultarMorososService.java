package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ConsultarMorososUseCase;
import com.condohub.gestion.application.port.out.MorosoRepository;
import com.condohub.gestion.domain.model.Moroso;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ConsultarMorososService implements ConsultarMorososUseCase {

    private final MorosoRepository morosos;

    public ConsultarMorososService(MorosoRepository morosos) {
        this.morosos = morosos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Moroso> consultar() {
        return morosos.morosos().stream()
                .sorted(Comparator.comparing((Moroso m) -> m.deudaVencida().monto())
                        .reversed()
                        .thenComparing(Moroso::codigo))
                .toList();
    }
}