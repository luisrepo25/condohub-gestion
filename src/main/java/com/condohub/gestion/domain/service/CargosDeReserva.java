package com.condohub.gestion.domain.service;

import com.condohub.gestion.domain.model.CargoDeReserva;
import com.condohub.gestion.domain.valueobject.Dinero;
import com.condohub.gestion.domain.valueobject.Periodo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * R5: los cargos de una reserva. El salón de eventos genera costo de uso y garantía; las demás áreas solo costo de uso,
 * que puede ser 0. Un monto 0 no genera cargo (un cargo siempre es mayor a 0, F10).
 *
 * <p>R5: vencen como el recargo (F5), el día de vencimiento (F4) del mes siguiente a su aplicación, así nunca nacen
 * vencidos y el residente tiene tiempo de pagarlos.
 */
public final class CargosDeReserva {

    public List<CargoDeReserva> para(String areaComun, Dinero costoUso, Dinero garantia, LocalDate hoy, int diaVencimiento) {
        LocalDate vence = Periodo.delMes(hoy).siguiente().fechaVencimiento(diaVencimiento);
        String area = areaComun == null || areaComun.isBlank() ? "área común" : areaComun.trim();
        List<CargoDeReserva> cargos = new ArrayList<>();
        if (costoUso.monto().signum() > 0) {
            cargos.add(new CargoDeReserva(CargoDeReserva.Tipo.USO_AREA, "Uso de " + area, costoUso, vence));
        }
        if (garantia.monto().signum() > 0) {
            cargos.add(new CargoDeReserva(CargoDeReserva.Tipo.GARANTIA, "Garantía de " + area, garantia, vence));
        }
        return List.copyOf(cargos);
    }
}
