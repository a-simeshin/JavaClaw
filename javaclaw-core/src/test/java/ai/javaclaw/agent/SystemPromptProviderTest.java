package ai.javaclaw.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ai.javaclaw.files.VirtualFile;
import ai.javaclaw.files.VirtualFileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemPromptProviderTest {

    @Mock
    VirtualFileRepository repositoryMock;

    SystemPromptProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SystemPromptProvider(repositoryMock);
    }

    @Test
    void load_withAgentMd_includesContent() {
        final VirtualFile agentMd = VirtualFile.newGlobalFile("AGENT.md", "You are a helpful agent.", "text/markdown");
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md")).thenReturn(Optional.of(agentMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.empty());

        final String result = provider.load();

        assertThat(result).contains("You are a helpful agent.");
    }

    @Test
    void load_withAllFiles_includesAll() {
        final VirtualFile agentMd = VirtualFile.newGlobalFile("AGENT.md", "Agent instructions.", "text/markdown");
        final VirtualFile soulMd = VirtualFile.newGlobalFile("SOUL.md", "Soul content.", "text/markdown");
        final VirtualFile infoMd = VirtualFile.newGlobalFile("INFO.md", "Info content.", "text/markdown");
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md")).thenReturn(Optional.of(agentMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.of(soulMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.of(infoMd));

        final String result = provider.load();

        assertThat(result).contains("Agent instructions.");
        assertThat(result).contains("Soul content.");
        assertThat(result).contains("Info content.");
    }

    @Test
    void load_missingFile_skipsGracefully() {
        final VirtualFile agentMd = VirtualFile.newGlobalFile("AGENT.md", "Agent instructions.", "text/markdown");
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md")).thenReturn(Optional.of(agentMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.empty());

        final String result = provider.load();

        assertThat(result).contains("Agent instructions.");
        assertThat(result).doesNotContain("Soul content.");
        assertThat(result).doesNotContain("Info content.");
    }

    @Test
    void load_emptyDb_returnsEnvOnly() {
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.empty());

        final String result = provider.load();

        // No workspace content, but environment block should still be present
        assertThat(result).contains("# Environment");
    }

    @Test
    void load_repositoryThrows_skipsGracefullyWithoutException() {
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md"))
                .thenThrow(new RuntimeException("DB connection lost"));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.empty());

        // Must not throw
        final String result = provider.load();

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("AGENT.md");
    }

    @Test
    void load_filesOrderedAgentThenSoulThenInfo() {
        final VirtualFile agentMd = VirtualFile.newGlobalFile("AGENT.md", "FIRST", "text/markdown");
        final VirtualFile soulMd = VirtualFile.newGlobalFile("SOUL.md", "SECOND", "text/markdown");
        final VirtualFile infoMd = VirtualFile.newGlobalFile("INFO.md", "THIRD", "text/markdown");
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md")).thenReturn(Optional.of(agentMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.of(soulMd));
        when(repositoryMock.findByOwnerIdIsNullAndPath("INFO.md")).thenReturn(Optional.of(infoMd));

        final String result = provider.load();

        final int firstPos = result.indexOf("FIRST");
        final int secondPos = result.indexOf("SECOND");
        final int thirdPos = result.indexOf("THIRD");
        assertThat(firstPos).isLessThan(secondPos);
        assertThat(secondPos).isLessThan(thirdPos);
    }
}
