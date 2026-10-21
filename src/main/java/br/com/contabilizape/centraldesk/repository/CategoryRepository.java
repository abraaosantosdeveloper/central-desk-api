package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.dto.CategoryUsageResponse;
import br.com.contabilizape.centraldesk.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByArchivedAtIsNullOrderByLabel();

    boolean existsByLabelIgnoreCase(String label);

    @Query("""
            SELECT new br.com.contabilizape.centraldesk.dto.CategoryUsageResponse(c.id, COUNT(t.id))
            FROM Category c LEFT JOIN Ticket t ON t.category = c
            WHERE c.archivedAt IS NULL
            GROUP BY c.id
            """)
    List<CategoryUsageResponse> countTicketsPerActiveCategory();
}
