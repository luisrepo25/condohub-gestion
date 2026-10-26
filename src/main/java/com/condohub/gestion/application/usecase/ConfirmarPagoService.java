package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.ConfirmarPagoUseCase;
import com.condohub.gestion.application.port.out.EventPublisher;
import com.condohub.gestion.application.port.out.RevisionPagoRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.service.ImputadorPagos;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ConfirmarPagoService implements ConfirmarPagoUseCase {

    private final RevisionPagoRepository pagos;
    private final ImputadorPagos imputador;
    private final EventPublisher eventos;

    public ConfirmarPagoService(RevisionPagoRepository pagos, ImputadorPagos imputador, EventPublisher eventos) {
        this.pagos = pagos;
        this.imputador = imputador;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public PagoConfirmado confirmar(String adminSub, UUID pagoId) {
        var pago = pagos.porIdParaRevisar(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + pagoId));
        var resultado = pago.confirmar(pagos.cargosPendientes(pago.unidadId()), imputador);
        long reciboNumero = pagos.confirmar(pagoId, adminSub, resultado);
        // S4: el evento queda en el outbox en la misma transacción; n8n envía el recibo (F8).
        eventos.publicar(new com.condohub.gestion.domain.event.PagoConfirmado(
                pagoId, pago.unidadId(), pago.monto(), reciboNumero));
        return new PagoConfirmado(pagos.pago(pagoId), reciboNumero, resultado.imputado(), resultado.excedente());
    }
}
