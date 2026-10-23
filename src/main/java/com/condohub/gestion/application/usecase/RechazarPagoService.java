package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.RechazarPagoUseCase;
import com.condohub.gestion.application.port.out.RevisionPagoRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RechazarPagoService implements RechazarPagoUseCase {

    private final RevisionPagoRepository pagos;

    public RechazarPagoService(RevisionPagoRepository pagos) {
        this.pagos = pagos;
    }

    @Override
    @Transactional
    public PagoRechazado rechazar(String adminSub, UUID pagoId, String motivo) {
        var pago = pagos.porIdParaRevisar(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
        String motivoGuardado = pago.rechazar(motivo);
        pagos.rechazar(pagoId, adminSub, motivoGuardado);
        return new PagoRechazado(pagos.pago(pagoId), motivoGuardado);
    }
}
