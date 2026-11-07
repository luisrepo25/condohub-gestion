package com.condohub.gestion.adapter.config;

import com.condohub.gestion.adapter.out.storage.AlmacenComprobantesNoDisponible;
import com.condohub.gestion.adapter.out.storage.S3AlmacenComprobantes;
import com.condohub.gestion.adapter.out.storage.S3UrlPrefirmada;
import com.condohub.gestion.application.port.in.ConsultarUrlComprobanteUseCase;
import com.condohub.gestion.application.port.in.RegistrarPagoUseCase;
import com.condohub.gestion.application.port.in.SolicitarSubidaComprobanteUseCase;
import com.condohub.gestion.application.port.out.AlmacenComprobantes;
import com.condohub.gestion.application.port.out.ComprobantePagoRepository;
import com.condohub.gestion.application.port.out.EstadoDeCuentaRepository;
import com.condohub.gestion.application.port.out.UnidadRepository;
import com.condohub.gestion.application.usecase.ConsultarUrlComprobanteService;
import com.condohub.gestion.application.usecase.RegistrarPagoConComprobanteService;
import com.condohub.gestion.application.usecase.SolicitarSubidaComprobanteService;
import com.condohub.gestion.domain.service.PoliticaVisibilidad;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Foto del comprobante de pago en S3 (F8). Sin las variables {@code S3_COMPROBANTES_*} el sistema sigue sin foto. */
@Configuration
class ComprobantesBeansConfig {

    @Bean
    AlmacenComprobantes almacenComprobantes(
            @Value("${S3_COMPROBANTES_BUCKET:}") String bucket,
            @Value("${S3_COMPROBANTES_ACCESS_KEY:}") String accessKey,
            @Value("${S3_COMPROBANTES_SECRET_KEY:}") String secretKey,
            @Value("${S3_COMPROBANTES_REGION:us-east-1}") String region,
            @Value("${S3_COMPROBANTES_VIGENCIA_SEGUNDOS:900}") int vigenciaSegundos,
            Clock reloj) {
        if (bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            return new AlmacenComprobantesNoDisponible();
        }
        return new S3AlmacenComprobantes(
                new S3UrlPrefirmada(accessKey, secretKey, region), bucket, region, vigenciaSegundos, reloj);
    }

    @Bean
    @Primary
    RegistrarPagoUseCase registrarPagoConComprobante(
            @Qualifier("registrarPago") RegistrarPagoUseCase base,
            ComprobantePagoRepository comprobantes,
            EstadoDeCuentaRepository cuentas) {
        return new RegistrarPagoConComprobanteService(base, comprobantes, cuentas);
    }

    @Bean
    SolicitarSubidaComprobanteUseCase solicitarSubidaComprobante(
            AlmacenComprobantes almacen,
            EstadoDeCuentaRepository cuentas,
            UnidadRepository unidades,
            PoliticaVisibilidad politica) {
        return new SolicitarSubidaComprobanteService(almacen, cuentas, unidades, politica);
    }

    @Bean
    ConsultarUrlComprobanteUseCase consultarUrlComprobante(
            ComprobantePagoRepository comprobantes,
            AlmacenComprobantes almacen,
            UnidadRepository unidades,
            PoliticaVisibilidad politica) {
        return new ConsultarUrlComprobanteService(comprobantes, almacen, unidades, politica);
    }
}
