package com.condohub.gestion.application.usecase;

import com.condohub.gestion.application.port.in.SimularPagoUseCase;
import com.condohub.gestion.application.port.out.CargosDeUnidadRepository;
import com.condohub.gestion.application.port.out.CargosDeUnidadRepository.CargoDeUnidad;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.domain.exception.RecursoNoEncontradoException;
import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.model.CargoPendiente;
import com.condohub.gestion.domain.model.Rol;
import com.condohub.gestion.domain.model.Solicitante;
import com.condohub.gestion.domain.service.ImputadorPagos;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import com.condohub.gestion.domain.valueobject.Dinero;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class SimularPagoService implements SimularPagoUseCase {

    private final CargosDeUnidadRepository cargos;
    private final UnidadRepository unidades;
    private final PoliticaVisibilidad politica;
    private final ImputadorPagos imputador;

    public SimularPagoService(
            CargosDeUnidadRepository cargos,
            UnidadRepository unidades,
            PoliticaVisibilidad politica,
            ImputadorPagos imputador) {
        this.cargos = cargos;
        this.unidades = unidades;
        this.politica = politica;
        this.imputador = imputador;
    }

    @Override
    @Transactional(readOnly = true)
    public Simulacion simular(Solicitante solicitante, UUID unidadId, BigDecimal monto) {
        // F1 primero: quien no puede ver la unidad no averigua si existe.
        Set<UUID> propias = solicitante.tieneRol(Rol.ADMIN) ? Set.of() : unidades.idsDeUnidadesDe(solicitante.sub());
        politica.verificarPuedeVerEstadoDeCuenta(solicitante, unidadId, propias);
        if (monto == null || monto.signum() <= 0) {
            throw new ReglaNegocioVioladaException("F10", "El monto del pago debe ser mayor a 0");
        }
        var aSimular = Dinero.de(monto);
        if (!cargos.existeUnidad(unidadId)) {
            throw new RecursoNoEncontradoException("No existe la unidad " + unidadId);
        }
        List<CargoDeUnidad> pendientes = cargos.pendientes(unidadId);
        // La misma regla de la confirmación (F7): el reparto no se reimplementa aquí.
        var resultado = imputador.imputar(
                aSimular,
                pendientes.stream()
                        .map(c -> new CargoPendiente(c.id(), c.saldo(), c.fechaVencimiento(), c.creadoEn()))
                        .toList());
        Map<UUID, CargoDeUnidad> porId =
                pendientes.stream().collect(Collectors.toMap(CargoDeUnidad::id, Function.identity()));
        var lineas = resultado.imputaciones().stream()
                .map(i -> {
                    var c = porId.get(i.cargoId());
                    return new Linea(
                            c.id(),
                            c.tipo(),
                            c.concepto(),
                            c.fechaVencimiento(),
                            c.saldo(),
                            i.monto(),
                            Dinero.de(c.saldo().monto().subtract(i.monto().monto())));
                })
                .toList();
        return new Simulacion(lineas, resultado.excedente());
    }
}
