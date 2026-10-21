package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.CategoryApi;
import br.com.contabilizape.centraldesk.dto.CategoryRequest;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.CategoryUsageResponse;
import br.com.contabilizape.centraldesk.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
public class CategoryController implements CategoryApi {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(request);
    }

    @Override
    @PatchMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        categoryService.archive(id);
    }

    @Override
    @GetMapping("/usage")
    public List<CategoryUsageResponse> usage() {
        return categoryService.usage();
    }
}
