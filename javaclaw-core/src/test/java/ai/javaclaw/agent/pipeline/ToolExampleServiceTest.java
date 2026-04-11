package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ToolExampleServiceTest {

    private ToolExampleRepository repository;
    private ToolExampleService service;

    @BeforeEach
    void setUp() {
        repository = mock(ToolExampleRepository.class);
        service = new ToolExampleService(repository);
    }

    @Test
    @DisplayName("findAll without toolName returns findAllGlobal")
    void findAll_noFilter_returnsAllGlobal() {
        final ToolExample ex = ToolExample.create("testTool", null, 0, "user", null, "call()", null);
        when(repository.findAllGlobal()).thenReturn(List.of(ex));

        final List<ToolExample> result = service.findAll(null);

        assertThat(result).hasSize(1);
        verify(repository).findAllGlobal();
        verify(repository, never()).findGlobalByToolName(any());
    }

    @Test
    @DisplayName("findAll with blank toolName returns findAllGlobal")
    void findAll_blankFilter_returnsAllGlobal() {
        when(repository.findAllGlobal()).thenReturn(List.of());

        service.findAll("   ");

        verify(repository).findAllGlobal();
        verify(repository, never()).findGlobalByToolName(any());
    }

    @Test
    @DisplayName("findAll with toolName filters by tool name")
    void findAll_withFilter_returnsFiltered() {
        when(repository.findGlobalByToolName("testTool")).thenReturn(List.of());

        service.findAll("testTool");

        verify(repository).findGlobalByToolName("testTool");
        verify(repository, never()).findAllGlobal();
    }

    @Test
    @DisplayName("findById returns entity when present")
    void findById_returnsEntity() {
        final ToolExample ex = new ToolExample("id-1", "tool", null, 0, "u", null, "c()", null);
        when(repository.findById("id-1")).thenReturn(Optional.of(ex));

        assertThat(service.findById("id-1")).isSameAs(ex);
    }

    @Test
    @DisplayName("findById throws when missing")
    void findById_throwsWhenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("create persists new example")
    void create_savesNewExample() {
        final ToolExample saved = ToolExample.create("testTool", null, 0, "user msg", null, "call()", null);
        when(repository.save(any(ToolExample.class))).thenReturn(saved);

        final ToolExample result = service.create("testTool", null, 0, "user msg", null, "call()", null);

        assertThat(result).isNotNull();
        verify(repository).save(any(ToolExample.class));
    }

    @Test
    @DisplayName("update replaces fields but preserves ownerId")
    void update_preservesOwnerId() {
        final ToolExample existing = new ToolExample("id-1", "tool", "owner-x", 0, "u", null, "c()", null);
        when(repository.findById("id-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ToolExample.class))).thenAnswer(inv -> inv.getArgument(0));

        final ToolExample result = service.update("id-1", "tool-new", 2, "u2", "a2", "c2()", "r2");

        assertThat(result.id()).isEqualTo("id-1");
        assertThat(result.toolName()).isEqualTo("tool-new");
        assertThat(result.ownerId()).isEqualTo("owner-x");
        assertThat(result.exampleOrder()).isEqualTo(2);
        assertThat(result.userMessage()).isEqualTo("u2");
        assertThat(result.assistantMessage()).isEqualTo("a2");
        assertThat(result.toolCall()).isEqualTo("c2()");
        assertThat(result.toolResult()).isEqualTo("r2");
    }

    @Test
    @DisplayName("delete calls repository deleteById")
    void delete_callsRepository() {
        service.delete("id-1");
        verify(repository).deleteById("id-1");
    }
}
