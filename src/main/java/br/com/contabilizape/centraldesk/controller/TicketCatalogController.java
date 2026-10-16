package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.TicketCatalogApi;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.PriorityResponse;
import br.com.contabilizape.centraldesk.service.TicketCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TicketCatalogController implements TicketCatalogApi {

    private final TicketCatalogService catalogService;

    public TicketCatalogController(TicketCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories();
    }

    @Override
    @GetMapping("/priorities")
    public List<PriorityResponse> priorities() {
        return catalogService.listPriorities();
    }
}
