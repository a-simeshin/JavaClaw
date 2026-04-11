package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.javaclaw.memory.Memory;
import ai.javaclaw.memory.MemoryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryToolTest {

    @Mock
    private MemoryService memoryService;

    private MemoryTool tool;

    @BeforeEach
    void setUp() {
        tool = MemoryTool.builder().memoryService(memoryService).build();
    }

    // ── store ──────────────────────────────────────────────────────────────────

    @Test
    void store_success_returnsConfirmation() {
        Memory saved = memory("id-1", "user-lang", "Java", "preference");
        when(memoryService.store(isNull(), eq("user-lang"), eq("Java"), eq("preference")))
                .thenReturn(saved);

        String result = tool.store("user-lang", "Java", "preference");

        assertThat(result).contains("Remembered").contains("user-lang");
        verify(memoryService).store(null, "user-lang", "Java", "preference");
    }

    @Test
    void store_blankKey_returnsError() {
        String result = tool.store("", "content", null);
        assertThat(result).contains("Error").contains("key must not be empty");
    }

    @Test
    void store_blankContent_returnsError() {
        String result = tool.store("key", "", null);
        assertThat(result).contains("Error").contains("content must not be empty");
    }

    // ── recall ─────────────────────────────────────────────────────────────────

    @Test
    void recall_found_returnsContent() {
        Memory m = memory("id-1", "deadline", "2026-05-01", "project");
        when(memoryService.recall(isNull(), eq("deadline"))).thenReturn(Optional.of(m));

        String result = tool.recall("deadline");

        assertThat(result).contains("deadline").contains("2026-05-01").contains("project");
    }

    @Test
    void recall_notFound_returnsMessage() {
        when(memoryService.recall(isNull(), eq("unknown"))).thenReturn(Optional.empty());

        String result = tool.recall("unknown");

        assertThat(result).contains("No memory found").contains("unknown");
    }

    @Test
    void recall_blankKey_returnsError() {
        String result = tool.recall("");
        assertThat(result).contains("Error").contains("key must not be empty");
    }

    // ── forget ─────────────────────────────────────────────────────────────────

    @Test
    void forget_existing_returnsConfirmation() {
        when(memoryService.forget(isNull(), eq("old-fact"))).thenReturn(true);

        String result = tool.forget("old-fact");

        assertThat(result).contains("Forgot").contains("old-fact");
    }

    @Test
    void forget_notFound_returnsMessage() {
        when(memoryService.forget(isNull(), eq("nope"))).thenReturn(false);

        String result = tool.forget("nope");

        assertThat(result).contains("No memory found").contains("nope");
    }

    // ── list ───────────────────────────────────────────────────────────────────

    @Test
    void list_withMemories_returnsFormattedList() {
        Memory m1 = memory("id-1", "lang", "Java", "preference");
        Memory m2 = memory("id-2", "deadline", "May 2026", null);
        when(memoryService.list(isNull())).thenReturn(List.of(m1, m2));

        String result = tool.list();

        assertThat(result)
                .contains("Memories (2)")
                .contains("lang")
                .contains("Java")
                .contains("deadline");
    }

    @Test
    void list_empty_returnsNoMemories() {
        when(memoryService.list(isNull())).thenReturn(List.of());

        String result = tool.list();

        assertThat(result).contains("No memories stored");
    }

    // ── search ─────────────────────────────────────────────────────────────────

    @Test
    void search_found_returnsResults() {
        Memory m = memory("id-1", "project-stack", "Spring Boot + React", "project");
        when(memoryService.search(isNull(), eq("Spring"))).thenReturn(List.of(m));

        String result = tool.search("Spring");

        assertThat(result).contains("Found 1").contains("project-stack").contains("Spring Boot");
    }

    @Test
    void search_notFound_returnsMessage() {
        when(memoryService.search(isNull(), eq("xyz"))).thenReturn(List.of());

        String result = tool.search("xyz");

        assertThat(result).contains("No memories matching").contains("xyz");
    }

    @Test
    void search_blankQuery_returnsError() {
        String result = tool.search("");
        assertThat(result).contains("Error").contains("query must not be empty");
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private static Memory memory(String id, String key, String content, String category) {
        return new Memory(id, null, key, content, category, Instant.now(), Instant.now());
    }
}
