package com.condohub.gestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.condohub.gestion.domain.exception.ReglaNegocioVioladaException;
import com.condohub.gestion.domain.valueobject.ClaveComprobante;
import com.condohub.gestion.domain.valueobject.IdempotencyKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** F8 (foto del comprobante): la foto vive en una carpeta por unidad y solo se enlaza al pago de esa unidad. */
class ClaveComprobanteTest {

    private final IdempotencyKey llave = IdempotencyKey.de("3f2b8c1e-7a4d-4e6f-9b0a-1c2d3e4f5a6b");

    @Test
    void F8_la_clave_va_en_la_carpeta_de_la_unidad_con_la_idempotency_key_y_la_extension() {
        assertThat(ClaveComprobante.para("A-101", llave, "image/jpeg"))
                .isEqualTo("comprobantes/A-101/3f2b8c1e-7a4d-4e6f-9b0a-1c2d3e4f5a6b.jpg");
        assertThat(ClaveComprobante.para("A-101", llave, "image/png")).endsWith(".png");
        assertThat(ClaveComprobante.para("A-101", llave, "application/pdf")).endsWith(".pdf");
    }

    @Test
    void F8_un_tipo_de_archivo_no_admitido_se_rechaza() {
        assertThatThrownBy(() -> ClaveComprobante.para("A-101", llave, "text/html"))
                .isInstanceOfSatisfying(ReglaNegocioVioladaException.class, e -> assertThat(e.codigo()).isEqualTo("F8"));
    }

    @Test
    void F8_una_clave_solo_pertenece_a_la_unidad_de_su_carpeta() {
        String clave = ClaveComprobante.para("A-101", llave, "image/jpeg");

        assertThat(ClaveComprobante.perteneceA("A-101", clave)).isTrue();
        assertThat(ClaveComprobante.perteneceA("A-102", clave)).isFalse();
    }

    @Test
    void F8_no_se_acepta_una_clave_inventada_ni_con_rutas_raras() {
        assertThat(ClaveComprobante.perteneceA("A-101", "comprobantes/A-101/../A-102/x.jpg")).isFalse();
        assertThat(ClaveComprobante.perteneceA("A-101", "comprobantes/A-101/" + UUID.randomUUID() + ".exe")).isFalse();
        assertThat(ClaveComprobante.perteneceA("A-101", "respaldos/azure/gestion.dump")).isFalse();
    }
}
