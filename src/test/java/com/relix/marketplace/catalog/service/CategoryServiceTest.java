package com.relix.marketplace.catalog.service;

import com.relix.marketplace.catalog.dto.CategoryResponse;
import com.relix.marketplace.catalog.dto.CategoryUpsertRequest;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.worker.entity.Credential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("public tree nests active children and hides an inactive subtree")
    void getPublicTree_buildsVisibleHierarchy() {
        Category home = category(1L, "HOME", "Home", true, null);
        Category cleaning = category(2L, "CLEANING", "Cleaning", true, home);
        Category hidden = category(3L, "HIDDEN", "Hidden", false, null);
        Category hiddenChild = category(4L, "HIDDEN_CHILD", "Hidden Child", true, hidden);
        when(categoryRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(cleaning, hiddenChild, home, hidden));

        List<CategoryResponse> result = categoryService.getPublicTree();

        assertEquals(1, result.size());
        assertEquals("HOME", result.get(0).getCode());
        assertEquals(List.of("CLEANING"), result.get(0).getChildren().stream()
                .map(CategoryResponse::getCode)
                .toList());
    }

    @Test
    @DisplayName("create normalizes the code and defaults active to true")
    void create_normalizesCodeAndDefaultsActive() {
        CategoryUpsertRequest request = request("home_cleaning", "Home Cleaning", null);
        when(categoryRepository.findByCode("HOME_CLEANING")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CategoryResponse response = categoryService.create(request);

        assertEquals("HOME_CLEANING", response.getCode());
        assertTrue(response.isActive());
        assertNull(response.getParentId());
    }

    @Test
    @DisplayName("create rejects a duplicate normalized code")
    void create_duplicateCode_rejected() {
        Category existing = category(5L, "TUTORING", "Tutoring", true, null);
        when(categoryRepository.findByCode("TUTORING")).thenReturn(Optional.of(existing));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> categoryService.create(request("tutoring", "Duplicate", null)));

        assertEquals("CATEGORY_CODE_EXISTS", exception.getCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("update rejects assigning the category as its own parent")
    void update_selfParent_rejected() {
        Category category = category(1L, "HOME", "Home", true, null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByCode("HOME")).thenReturn(Optional.of(category));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.update(1L, request("HOME", "Home", 1L)));

        assertEquals("CATEGORY_HIERARCHY_CYCLE", exception.getCode());
    }

    @Test
    @DisplayName("update rejects assigning a descendant as parent")
    void update_descendantParent_rejected() {
        Category root = category(1L, "ROOT", "Root", true, null);
        Category child = category(2L, "CHILD", "Child", true, root);
        Category grandchild = category(3L, "GRANDCHILD", "Grandchild", true, child);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.findByCode("ROOT")).thenReturn(Optional.of(root));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(grandchild));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> categoryService.update(1L, request("ROOT", "Root", 3L)));

        assertEquals("CATEGORY_HIERARCHY_CYCLE", exception.getCode());
    }

    @Test
    @DisplayName("deactivate preserves children while hiding the category")
    void deactivate_updatesOnlyActiveFlag() {
        Category category = category(7L, "EVENT_HELP", "Event Help", true, null);
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        CategoryResponse response = categoryService.deactivate(7L);

        assertFalse(response.isActive());
        verify(categoryRepository).save(category);
    }

    private Category category(
            Long id,
            String code,
            String name,
            boolean active,
            Category parent) {
        Category category = Category.builder()
                .code(code)
                .name(name)
                .active(active)
                .parent(parent)
                .build();
        category.setId(id);
        return category;
    }

    private CategoryUpsertRequest request(String code, String name, Long parentId) {
        CategoryUpsertRequest request = new CategoryUpsertRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription("Description");
        request.setIcon("icon");
        request.setRequiredCredential(Credential.Type.BACKGROUND_CHECK);
        request.setParentId(parentId);
        return request;
    }
}
