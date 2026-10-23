package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.valueobject.Alicuota;
import java.util.Map;
import java.util.UUID;

public interface AlicuotaRepository {

    /** Alícuota de cada unidad activa, por id de unidad, ordenadas por código de unidad (F2, F3). */
    Map<UUID, Alicuota> deUnidadesActivas();
}
