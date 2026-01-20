package com.naag.categoryadmin.service;

import com.naag.categoryadmin.metrics.CategoryAdminMetrics;
import com.naag.categoryadmin.model.Category;
import com.naag.categoryadmin.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CategoryService.
 * Tests category management functionality.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryAdminMetrics metrics;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, metrics);
    }

    @Nested
    @DisplayName("Get Categories Tests")
    class GetCategoriesTests {

        @Test
        @DisplayName("Should get all categories")
        void shouldGetAllCategories() {
            // Given
            Category cat1 = Category.builder().id("cat-1").name("Category 1").build();
            Category cat2 = Category.builder().id("cat-2").name("Category 2").build();
            when(categoryRepository.findAll()).thenReturn(List.of(cat1, cat2));

            // When
            List<Category> categories = categoryService.getAllCategories();

            // Then
            assertThat(categories).hasSize(2);
            assertThat(categories).extracting(Category::getName)
                    .containsExactly("Category 1", "Category 2");
        }

        @Test
        @DisplayName("Should get category by ID")
        void shouldGetCategoryById() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Test Category")
                    .description("Test description")
                    .build();
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            Optional<Category> result = categoryService.getCategory("cat-1");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Test Category");
        }

        @Test
        @DisplayName("Should return empty when category not found")
        void shouldReturnEmptyWhenCategoryNotFound() {
            // Given
            when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When
            Optional<Category> result = categoryService.getCategory("nonexistent");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should get only active categories")
        void shouldGetOnlyActiveCategories() {
            // Given
            Category activeCat = Category.builder().id("cat-1").name("Active").active(true).build();
            when(categoryRepository.findByActiveTrue()).thenReturn(List.of(activeCat));

            // When
            List<Category> categories = categoryService.getActiveCategories();

            // Then
            assertThat(categories).hasSize(1);
            assertThat(categories.get(0).isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Create Category Tests")
    class CreateCategoryTests {

        @Test
        @DisplayName("Should create category with provided ID")
        void shouldCreateCategoryWithProvidedId() {
            // Given
            Category input = Category.builder()
                    .id("custom-id")
                    .name("New Category")
                    .description("Description")
                    .active(true)
                    .build();

            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category created = categoryService.createCategory(input);

            // Then
            assertThat(created.getId()).isEqualTo("custom-id");
            assertThat(created.getName()).isEqualTo("New Category");
            verify(metrics).recordCategoryCreated();
        }

        @Test
        @DisplayName("Should generate ID when not provided")
        void shouldGenerateIdWhenNotProvided() {
            // Given
            Category input = Category.builder()
                    .name("New Category")
                    .description("Description")
                    .build();

            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category created = categoryService.createCategory(input);

            // Then
            assertThat(created.getId()).isNotNull().isNotBlank();
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should initialize empty tool list")
        void shouldInitializeEmptyToolList() {
            // Given
            Category input = Category.builder()
                    .id("cat-1")
                    .name("New Category")
                    .build();

            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category created = categoryService.createCategory(input);

            // Then
            assertThat(created.getToolIds()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Should update category count after creation")
        void shouldUpdateCategoryCountAfterCreation() {
            // Given
            Category input = Category.builder().id("cat-1").name("Category").build();
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
            when(categoryRepository.count()).thenReturn(5L);

            // When
            categoryService.createCategory(input);

            // Then
            verify(metrics).setTotalCategoryCount(5);
        }
    }

    @Nested
    @DisplayName("Update Category Tests")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Should update category fields")
        void shouldUpdateCategoryFields() {
            // Given
            Category existing = Category.builder()
                    .id("cat-1")
                    .name("Old Name")
                    .description("Old Description")
                    .active(true)
                    .toolIds(new ArrayList<>())
                    .build();

            Category updates = Category.builder()
                    .name("New Name")
                    .description("New Description")
                    .active(false)
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(existing));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category updated = categoryService.updateCategory("cat-1", updates);

            // Then
            assertThat(updated.getName()).isEqualTo("New Name");
            assertThat(updated.getDescription()).isEqualTo("New Description");
            assertThat(updated.isActive()).isFalse();
            verify(metrics).recordCategoryUpdated();
        }

        @Test
        @DisplayName("Should update tool IDs")
        void shouldUpdateToolIds() {
            // Given
            Category existing = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>(List.of("tool-1")))
                    .build();

            Category updates = Category.builder()
                    .toolIds(List.of("tool-2", "tool-3"))
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(existing));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category updated = categoryService.updateCategory("cat-1", updates);

            // Then
            assertThat(updated.getToolIds()).containsExactly("tool-2", "tool-3");
        }

        @Test
        @DisplayName("Should throw when category not found")
        void shouldThrowWhenCategoryNotFound() {
            // Given
            when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());
            Category updates = Category.builder().name("New Name").build();

            // When/Then
            assertThatThrownBy(() -> categoryService.updateCategory("nonexistent", updates))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category not found");
        }

        @Test
        @DisplayName("Should preserve existing values when update is null")
        void shouldPreserveExistingValuesWhenUpdateIsNull() {
            // Given
            Category existing = Category.builder()
                    .id("cat-1")
                    .name("Existing Name")
                    .description("Existing Description")
                    .active(true)
                    .build();

            Category updates = Category.builder().build(); // All null

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(existing));
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Category updated = categoryService.updateCategory("cat-1", updates);

            // Then
            assertThat(updated.getName()).isEqualTo("Existing Name");
            assertThat(updated.getDescription()).isEqualTo("Existing Description");
        }
    }

    @Nested
    @DisplayName("Delete Category Tests")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Should delete existing category")
        void shouldDeleteExistingCategory() {
            // Given
            Category category = Category.builder().id("cat-1").name("Category").build();
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.deleteCategory("cat-1");

            // Then
            verify(categoryRepository).delete(category);
            verify(metrics).recordCategoryDeleted();
        }

        @Test
        @DisplayName("Should do nothing when deleting nonexistent category")
        void shouldDoNothingWhenDeletingNonexistentCategory() {
            // Given
            when(categoryRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When
            categoryService.deleteCategory("nonexistent");

            // Then
            verify(categoryRepository, never()).delete(any());
            verify(metrics, never()).recordCategoryDeleted();
        }

        @Test
        @DisplayName("Should update category count after deletion")
        void shouldUpdateCategoryCountAfterDeletion() {
            // Given
            Category category = Category.builder().id("cat-1").name("Category").build();
            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
            when(categoryRepository.count()).thenReturn(4L);

            // When
            categoryService.deleteCategory("cat-1");

            // Then
            verify(metrics).setTotalCategoryCount(4);
        }
    }

    @Nested
    @DisplayName("Tool Management Tests")
    class ToolManagementTests {

        @Test
        @DisplayName("Should add tool to category")
        void shouldAddToolToCategory() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>())
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.addToolToCategory("cat-1", "tool-1");

            // Then
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getToolIds()).contains("tool-1");
        }

        @Test
        @DisplayName("Should not add duplicate tool")
        void shouldNotAddDuplicateTool() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>(List.of("tool-1")))
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.addToolToCategory("cat-1", "tool-1");

            // Then
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should initialize tool list if null when adding")
        void shouldInitializeToolListIfNullWhenAdding() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(null)
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.addToolToCategory("cat-1", "tool-1");

            // Then
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getToolIds()).contains("tool-1");
        }

        @Test
        @DisplayName("Should remove tool from category")
        void shouldRemoveToolFromCategory() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>(List.of("tool-1", "tool-2")))
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.removeToolFromCategory("cat-1", "tool-1");

            // Then
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getToolIds()).containsExactly("tool-2");
        }

        @Test
        @DisplayName("Should reorder tools")
        void shouldReorderTools() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>(List.of("tool-1", "tool-2", "tool-3")))
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.reorderTools("cat-1", List.of("tool-3", "tool-1", "tool-2"));

            // Then
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getToolIds()).containsExactly("tool-3", "tool-1", "tool-2");
        }

        @Test
        @DisplayName("Should reset tool priorities (sort alphabetically)")
        void shouldResetToolPriorities() {
            // Given
            Category category = Category.builder()
                    .id("cat-1")
                    .name("Category")
                    .toolIds(new ArrayList<>(List.of("zebra-tool", "alpha-tool", "mid-tool")))
                    .build();

            when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));

            // When
            categoryService.resetToolPriorities("cat-1");

            // Then
            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            assertThat(captor.getValue().getToolIds())
                    .containsExactly("alpha-tool", "mid-tool", "zebra-tool");
        }
    }

    @Nested
    @DisplayName("Default Categories Tests")
    class DefaultCategoriesTests {

        @Test
        @DisplayName("Should have correct default category IDs")
        void shouldHaveCorrectDefaultCategoryIds() {
            // These constants should be stable for vector DB consistency
            assertThat(CategoryService.CATEGORY_SERVICE_DEV).isEqualTo("service-development");
            assertThat(CategoryService.CATEGORY_BATCH_DEV).isEqualTo("batch-development");
            assertThat(CategoryService.CATEGORY_UI_DEV).isEqualTo("ui-development");
            assertThat(CategoryService.CATEGORY_MISC_DEV).isEqualTo("misc-development");
            assertThat(CategoryService.CATEGORY_DATA_RETRIEVAL).isEqualTo("data-retrieval");
        }
    }
}
