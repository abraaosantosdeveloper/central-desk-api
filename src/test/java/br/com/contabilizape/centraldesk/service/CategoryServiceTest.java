package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CategoryRequest;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.exception.CategoryDoesNotExistException;
import br.com.contabilizape.centraldesk.exception.CategoryLabelTakenException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID ID = UUID.fromString("dddddddd-0000-4000-8000-000000000004");

    @Mock private CategoryRepository categoryRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCategoryWithTrimmedLabelAndUpperCaseColor() {
        when(categoryRepository.saveAndFlush(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.create(new CategoryRequest("  Telefonia  ", "#0891b2"));

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLabel()).isEqualTo("Telefonia");
        assertThat(saved.getValue().getColor()).isEqualTo("#0891B2");
        assertThat(response.label()).isEqualTo("Telefonia");
    }

    @Test
    void repeatedLabelIsConflict() {
        when(categoryRepository.existsByLabelIgnoreCase("Hardware")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest("Hardware", "#2563EB")))
                .isInstanceOf(CategoryLabelTakenException.class);
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesLabelAndColor() {
        Category category = category("Impressoras", "#DB2777");
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category));

        CategoryResponse response = service.update(ID, new CategoryRequest("Impressão", "#7c3aed"));

        assertThat(category.getLabel()).isEqualTo("Impressão");
        assertThat(category.getColor()).isEqualTo("#7C3AED");
        assertThat(response.color()).isEqualTo("#7C3AED");
    }

    @Test
    void keepingTheSameLabelIsNotAConflict() {
        Category category = category("Impressoras", "#DB2777");
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category));

        service.update(ID, new CategoryRequest("impressoras", "#DB2777"));

        verify(categoryRepository, never()).existsByLabelIgnoreCase(any());
        assertThat(category.getLabel()).isEqualTo("impressoras");
    }

    @Test
    void renamingToAnExistingLabelIsConflict() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category("Impressoras", "#DB2777")));
        when(categoryRepository.existsByLabelIgnoreCase("Hardware")).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, new CategoryRequest("Hardware", "#DB2777")))
                .isInstanceOf(CategoryLabelTakenException.class);
    }

    @Test
    void removingArchivesInsteadOfDeleting() {
        Category category = category("Impressoras", "#DB2777");
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(category));

        service.archive(ID);

        assertThat(category.getArchivedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void unknownOrArchivedCategoryIsNotFound() {
        when(categoryRepository.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.archive(ID)).isInstanceOf(CategoryDoesNotExistException.class);

        Category archived = category("Antiga", "#64748B");
        archived.archive(NOW.atOffset(ZoneOffset.UTC));
        when(categoryRepository.findById(ID)).thenReturn(Optional.of(archived));
        assertThatThrownBy(() -> service.update(ID, new CategoryRequest("Nova", "#64748B")))
                .isInstanceOf(CategoryDoesNotExistException.class);
    }

    private static Category category(String label, String color) {
        Category category = new Category(label, color);
        ReflectionTestUtils.setField(category, "id", ID);
        return category;
    }
}
