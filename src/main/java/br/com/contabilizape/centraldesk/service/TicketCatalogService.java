package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.PriorityResponse;
import br.com.contabilizape.centraldesk.repository.CategoryRepository;
import br.com.contabilizape.centraldesk.repository.PriorityRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Opções do formulário de chamado: categorias (por nome) e prioridades (por urgência).
 */
@Service
public class TicketCatalogService {

    private final CategoryRepository categoryRepository;
    private final PriorityRepository priorityRepository;

    public TicketCatalogService(CategoryRepository categoryRepository, PriorityRepository priorityRepository) {
        this.categoryRepository = categoryRepository;
        this.priorityRepository = priorityRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll(Sort.by("label")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriorityResponse> listPriorities() {
        return priorityRepository.findAll(Sort.by("id")).stream().map(PriorityResponse::from).toList();
    }
}
