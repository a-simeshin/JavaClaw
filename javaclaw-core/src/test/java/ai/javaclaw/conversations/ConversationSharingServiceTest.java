package ai.javaclaw.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationSharingServiceTest {

    @Mock
    private ConversationShareRepository shareRepository;

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationSharingService service;

    @BeforeEach
    void setUp() {
        service = new ConversationSharingService(shareRepository, conversationRepository);
    }

    @Nested
    class Share {

        @Test
        void shareConversationSuccessfully() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);
            when(shareRepository.existsByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(false);
            when(shareRepository.save(any())).thenAnswer(inv -> {
                ConversationShare s = inv.getArgument(0);
                return new ConversationShare(
                        1L, s.conversationId(), s.sharedWith(), s.permission(), s.sharedBy(), s.createdAt());
            });

            ConversationShare result = service.share("conv-1", "owner-1", "user-2", "READ");

            assertThat(result.conversationId()).isEqualTo("conv-1");
            assertThat(result.sharedWith()).isEqualTo("user-2");
            assertThat(result.permission()).isEqualTo("READ");
            assertThat(result.sharedBy()).isEqualTo("owner-1");
        }

        @Test
        void shareWithWritePermission() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);
            when(shareRepository.existsByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(false);
            when(shareRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConversationShare result = service.share("conv-1", "owner-1", "user-2", "write");

            assertThat(result.permission()).isEqualTo("WRITE");
        }

        @Test
        void shareBlankConversationIdThrows() {
            assertThatThrownBy(() -> service.share("", "owner-1", "user-2", "READ"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("conversationId");
        }

        @Test
        void shareBlankTargetUserThrows() {
            assertThatThrownBy(() -> service.share("conv-1", "owner-1", "", "READ"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetUserId");
        }

        @Test
        void shareWithSelfThrows() {
            assertThatThrownBy(() -> service.share("conv-1", "owner-1", "owner-1", "READ"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yourself");
        }

        @Test
        void shareInvalidPermissionThrows() {
            assertThatThrownBy(() -> service.share("conv-1", "owner-1", "user-2", "EXECUTE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Permission must be READ or WRITE");
        }

        @Test
        void shareNotOwnedConversationThrows() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.share("conv-1", "owner-1", "user-2", "READ"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not owned");
        }

        @Test
        void shareAlreadySharedThrows() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);
            when(shareRepository.existsByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.share("conv-1", "owner-1", "user-2", "READ"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already shared");
        }
    }

    @Nested
    class Unshare {

        @Test
        void unshareSuccessfully() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);
            when(shareRepository.deleteByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(1);

            service.unshare("conv-1", "owner-1", "user-2");

            verify(shareRepository).deleteByConversationIdAndSharedWith("conv-1", "user-2");
        }

        @Test
        void unshareNotOwnedThrows() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.unshare("conv-1", "owner-1", "user-2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not owned");
        }
    }

    @Nested
    class ListShares {

        @Test
        void listSharesSuccessfully() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);
            var share1 = new ConversationShare(1L, "conv-1", "user-2", "READ", "owner-1", Instant.now());
            var share2 = new ConversationShare(2L, "conv-1", "user-3", "WRITE", "owner-1", Instant.now());
            when(shareRepository.findByConversationId("conv-1")).thenReturn(List.of(share1, share2));

            List<ConversationShare> result = service.listShares("conv-1", "owner-1");

            assertThat(result).hasSize(2);
        }

        @Test
        void listSharesNotOwnedThrows() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "not-owner"))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.listShares("conv-1", "not-owner"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class HasAccess {

        @Test
        void ownerHasAccess() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);

            assertThat(service.hasAccess("conv-1", "owner-1")).isTrue();
        }

        @Test
        void sharedUserHasAccess() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "user-2")).thenReturn(false);
            when(shareRepository.existsByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(true);

            assertThat(service.hasAccess("conv-1", "user-2")).isTrue();
        }

        @Test
        void unrelatedUserHasNoAccess() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "stranger"))
                    .thenReturn(false);
            when(shareRepository.existsByConversationIdAndSharedWith("conv-1", "stranger"))
                    .thenReturn(false);

            assertThat(service.hasAccess("conv-1", "stranger")).isFalse();
        }
    }

    @Nested
    class HasWriteAccess {

        @Test
        void ownerHasWriteAccess() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "owner-1"))
                    .thenReturn(true);

            assertThat(service.hasWriteAccess("conv-1", "owner-1")).isTrue();
        }

        @Test
        void sharedUserWithWritePermission() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "user-2")).thenReturn(false);
            var share = new ConversationShare(1L, "conv-1", "user-2", "WRITE", "owner-1", Instant.now());
            when(shareRepository.findByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(Optional.of(share));

            assertThat(service.hasWriteAccess("conv-1", "user-2")).isTrue();
        }

        @Test
        void sharedUserWithReadOnlyPermission() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "user-2")).thenReturn(false);
            var share = new ConversationShare(1L, "conv-1", "user-2", "READ", "owner-1", Instant.now());
            when(shareRepository.findByConversationIdAndSharedWith("conv-1", "user-2"))
                    .thenReturn(Optional.of(share));

            assertThat(service.hasWriteAccess("conv-1", "user-2")).isFalse();
        }

        @Test
        void noShareNoWriteAccess() {
            when(conversationRepository.existsByIdAndUserId("conv-1", "stranger"))
                    .thenReturn(false);
            when(shareRepository.findByConversationIdAndSharedWith("conv-1", "stranger"))
                    .thenReturn(Optional.empty());

            assertThat(service.hasWriteAccess("conv-1", "stranger")).isFalse();
        }
    }

    @Nested
    class GetSharedConversationIds {

        @Test
        void returnsSharedConversationIds() {
            var share1 = new ConversationShare(1L, "conv-1", "user-2", "READ", "owner-1", Instant.now());
            var share2 = new ConversationShare(2L, "conv-3", "user-2", "WRITE", "owner-2", Instant.now());
            when(shareRepository.findBySharedWith("user-2")).thenReturn(List.of(share1, share2));

            List<String> ids = service.getSharedConversationIds("user-2");

            assertThat(ids).containsExactly("conv-1", "conv-3");
        }

        @Test
        void returnsEmptyWhenNoShares() {
            when(shareRepository.findBySharedWith("user-2")).thenReturn(List.of());

            assertThat(service.getSharedConversationIds("user-2")).isEmpty();
        }
    }
}
