package com.condohub.gestion.application.port.out;

import com.condohub.gestion.domain.model.Moroso;
import java.util.List;

public interface MorosoRepository {

    List<Moroso> morosos();
}