package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ListarPagosPendientesUseCase;
import com.condohub.gestion.application.port.out.PagoRepository;
import com.condohub.gestion.domain.model.Pago;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ListarPagosPendientesService implements ListarPagosPendientesUseCase {

    private final PagoRepository pagos;

    public ListarPagosPendientesService(PagoRepository pagos) {
        this.pagos = pagos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> listar() {
        return pagos.pendientes();
    }
}
