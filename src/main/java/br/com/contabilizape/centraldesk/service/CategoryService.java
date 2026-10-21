package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CategoryRequest;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.CategoryUsageResponse;
import br.com.contabilizape.centraldesk.exception.CategoryDoesNotExistException;
import br.com.contabilizape.centraldesk.exception.CategoryLabelTakenException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Gestão de categorias pelo administrador (SCRUM-167). Remover arquiva: os chamados antigos mantêm a categoria.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String label = request.label().trim();
        if (categoryRepository.existsByLabelIgnoreCase(label)) {
            throw new CategoryLabelTakenException(label);
        }
        Category category = categoryRepository.saveAndFlush(new Category(label, normalizeColor(request.color())));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findActive(id);
        String label = request.label().trim();
        if (!label.equalsIgnoreCase(category.getLabel()) && categoryRepository.existsByLabelIgnoreCase(label)) {
            throw new CategoryLabelTakenException(label);
        }
        category.update(label, normalizeColor(request.color()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public void archive(UUID id) {
        findActive(id).archive(OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<CategoryUsageResponse> usage() {
        return categoryRepository.countTicketsPerActiveCategory();
    }

    private Category findActive(UUID id) {
        return categoryRepository.findById(id).filter(Category::isActive)
                .orElseThrow(() -> new CategoryDoesNotExistException(id));
    }

    private static String normalizeColor(String color) {
        return color.trim().toUpperCase(Locale.ROOT);
    }
}
