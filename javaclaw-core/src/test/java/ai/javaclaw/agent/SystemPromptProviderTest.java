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

    // -------------------------------------------------------------------------
    // Per-user identity tests (8.1 SOUL.md per-user)
    // -------------------------------------------------------------------------

    @Test
    void loadIdentity_withUserId_appendsUserFiles() {
        final String userId = "user-123";
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md"))
                .thenReturn(Optional.of(VirtualFile.newGlobalFile("AGENT.md", "Global agent.", "text/markdown")));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md"))
                .thenReturn(Optional.of(VirtualFile.newGlobalFile("SOUL.md", "Global soul.", "text/markdown")));
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_AGENT.md"))
                .thenReturn(
                        Optional.of(VirtualFile.newUserFile(userId, "USER_AGENT.md", "User agent.", "text/markdown")));
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_SOUL.md"))
                .thenReturn(
                        Optional.of(VirtualFile.newUserFile(userId, "USER_SOUL.md", "User soul.", "text/markdown")));

        final String result = provider.loadIdentity(userId);

        assertThat(result).contains("Global agent.");
        assertThat(result).contains("Global soul.");
        assertThat(result).contains("User agent.");
        assertThat(result).contains("User soul.");
        // Order: global first, then per-user
        assertThat(result.indexOf("Global agent.")).isLessThan(result.indexOf("User agent."));
        assertThat(result.indexOf("Global soul.")).isLessThan(result.indexOf("User soul."));
    }

    @Test
    void loadIdentity_withUserId_missingUserFiles_returnsGlobalOnly() {
        final String userId = "user-456";
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md"))
                .thenReturn(Optional.of(VirtualFile.newGlobalFile("AGENT.md", "Global agent.", "text/markdown")));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_AGENT.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_SOUL.md")).thenReturn(Optional.empty());

        final String result = provider.loadIdentity(userId);

        assertThat(result).contains("Global agent.");
        assertThat(result).doesNotContain("User");
    }

    @Test
    void loadIdentity_nullUserId_skipsUserFiles() {
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md"))
                .thenReturn(Optional.of(VirtualFile.newGlobalFile("AGENT.md", "Global agent.", "text/markdown")));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());

        final String result = provider.loadIdentity(null);

        assertThat(result).contains("Global agent.");
        // No per-user repository calls should have been made
    }

    @Test
    void loadIdentity_userFileDbError_skipsGracefully() {
        final String userId = "user-789";
        when(repositoryMock.findByOwnerIdIsNullAndPath("AGENT.md"))
                .thenReturn(Optional.of(VirtualFile.newGlobalFile("AGENT.md", "Global agent.", "text/markdown")));
        when(repositoryMock.findByOwnerIdIsNullAndPath("SOUL.md")).thenReturn(Optional.empty());
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_AGENT.md")).thenThrow(new RuntimeException("DB error"));
        when(repositoryMock.findByOwnerIdAndPath(userId, "USER_SOUL.md"))
                .thenReturn(
                        Optional.of(VirtualFile.newUserFile(userId, "USER_SOUL.md", "User soul.", "text/markdown")));

        final String result = provider.loadIdentity(userId);

        assertThat(result).contains("Global agent.");
        assertThat(result).contains("User soul.");
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
