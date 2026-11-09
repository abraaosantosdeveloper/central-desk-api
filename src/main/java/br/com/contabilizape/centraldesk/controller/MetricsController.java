package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.MetricsApi;
import br.com.contabilizape.centraldesk.dto.MetricsFilter;
import br.com.contabilizape.centraldesk.dto.MetricsResponse;
import br.com.contabilizape.centraldesk.service.MetricsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
public class MetricsController implements MetricsApi {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /** Administradores (tudo) e coordenadores (o próprio setor); os demais, 403 (regra no serviço, lida do banco). */
    @Override
    @GetMapping("/metrics")
    public MetricsResponse metrics(@AuthenticationPrincipal Jwt jwt, MetricsFilter filter) {
        return metricsService.metrics(UUID.fromString(jwt.getSubject()), filter);
    }
}
