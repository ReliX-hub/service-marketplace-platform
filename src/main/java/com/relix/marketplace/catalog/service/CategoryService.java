package com.relix.marketplace.catalog.service;

import com.relix.marketplace.catalog.dto.CategoryResponse;
import com.relix.marketplace.catalog.dto.CategoryUpsertRequest;
import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private static final Comparator<CategoryResponse> BY_NAME =
            Comparator.comparing(CategoryResponse::getName, String.CASE_INSENSITIVE_ORDER);

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getPublicTree() {
        List<Category> allCategories = categoryRepository.findAllByOrderByNameAsc();
        List<Category> visibleCategories = allCategories.stream()
                .filter(this::isPubliclyVisible)
                .toList();
        return buildTree(visibleCategories).roots();
    }

    public CategoryResponse getPublicByCode(String code) {
        String normalizedCode = normalizeCode(code);
        List<Category> allCategories = categoryRepository.findAllByOrderByNameAsc();
        Category category = allCategories.stream()
                .filter(candidate -> candidate.getCode().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> categoryNotFound(normalizedCode));

        if (!isPubliclyVisible(category)) {
            throw categoryNotFound(normalizedCode);
        }

        List<Category> visibleCategories = allCategories.stream()
                .filter(this::isPubliclyVisible)
                .toList();
        Tree tree = buildTree(visibleCategories);
        CategoryResponse response = tree.byId().get(category.getId());
        if (response == null) {
            throw categoryNotFound(normalizedCode);
        }
        return response;
    }

    @Transactional
    public CategoryResponse create(CategoryUpsertRequest request) {
        String normalizedCode = normalizeCode(request.getCode());
        assertCodeAvailable(normalizedCode, null);

        Category parent = resolveAndValidateParent(request.getParentId(), null);
        Category category = Category.builder()
                .code(normalizedCode)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .icon(trimToNull(request.getIcon()))
                .requiredCredential(request.getRequiredCredential())
                .parent(parent)
                .active(request.getActive() == null || request.getActive())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpsertRequest request) {
        Category category = getRequired(categoryId);
        String normalizedCode = normalizeCode(request.getCode());
        assertCodeAvailable(normalizedCode, categoryId);

        Category parent = resolveAndValidateParent(request.getParentId(), category);
        category.setCode(normalizedCode);
        category.setName(request.getName().trim());
        category.setDescription(trimToNull(request.getDescription()));
        category.setIcon(trimToNull(request.getIcon()));
        category.setRequiredCredential(request.getRequiredCredential());
        category.setParent(parent);
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse activate(Long categoryId) {
        return setActive(categoryId, true);
    }

    @Transactional
    public CategoryResponse deactivate(Long categoryId) {
        return setActive(categoryId, false);
    }

    private CategoryResponse setActive(Long categoryId, boolean active) {
        Category category = getRequired(categoryId);
        category.setActive(active);
        return toResponse(categoryRepository.save(category));
    }

    private Category getRequired(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private void assertCodeAvailable(String code, Long currentCategoryId) {
        categoryRepository.findByCode(code).ifPresent(existing -> {
            if (currentCategoryId == null || !existing.getId().equals(currentCategoryId)) {
                throw new ConflictException(
                        "Category code already exists: " + code,
                        "CATEGORY_CODE_EXISTS",
                        Map.of("code", code));
            }
        });
    }

    private Category resolveAndValidateParent(Long parentId, Category categoryBeingUpdated) {
        if (parentId == null) {
            return null;
        }

        Category parent = getRequired(parentId);
        if (categoryBeingUpdated == null) {
            return parent;
        }

        Long categoryId = categoryBeingUpdated.getId();
        Set<Long> visited = new HashSet<>();
        Category cursor = parent;
        while (cursor != null) {
            Long cursorId = cursor.getId();
            if (categoryId.equals(cursorId)) {
                throw hierarchyCycle(categoryId, parentId);
            }
            if (cursorId != null && !visited.add(cursorId)) {
                throw hierarchyCycle(categoryId, parentId);
            }
            cursor = cursor.getParent();
        }
        return parent;
    }

    private BusinessException hierarchyCycle(Long categoryId, Long parentId) {
        return new BusinessException(
                "A category cannot be its own parent or a child of its descendant",
                "CATEGORY_HIERARCHY_CYCLE",
                Map.of("categoryId", categoryId, "parentId", parentId));
    }

    private boolean isPubliclyVisible(Category category) {
        Set<Long> visited = new HashSet<>();
        Category cursor = category;
        while (cursor != null) {
            if (!Boolean.TRUE.equals(cursor.getActive())) {
                return false;
            }
            Long cursorId = cursor.getId();
            if (cursorId != null && !visited.add(cursorId)) {
                return false;
            }
            cursor = cursor.getParent();
        }
        return true;
    }

    private Tree buildTree(List<Category> categories) {
        Map<Long, CategoryResponse> byId = new HashMap<>();
        for (Category category : categories) {
            byId.put(category.getId(), toResponse(category));
        }

        List<CategoryResponse> roots = new ArrayList<>();
        for (Category category : categories) {
            CategoryResponse response = byId.get(category.getId());
            Long parentId = category.getParent() == null ? null : category.getParent().getId();
            CategoryResponse parent = parentId == null ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(response);
            } else {
                parent.getChildren().add(response);
            }
        }

        roots.sort(BY_NAME);
        byId.values().forEach(response -> response.getChildren().sort(BY_NAME));
        return new Tree(roots, byId);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .icon(category.getIcon())
                .requiredCredential(category.getRequiredCredential())
                .active(Boolean.TRUE.equals(category.getActive()))
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .children(new ArrayList<>())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Category code is required", "CATEGORY_CODE_REQUIRED");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResourceNotFoundException categoryNotFound(String code) {
        return new ResourceNotFoundException(
                "Active category not found: " + code,
                "CATEGORY_NOT_FOUND",
                Map.of("code", code));
    }

    private record Tree(List<CategoryResponse> roots, Map<Long, CategoryResponse> byId) {
    }
}
