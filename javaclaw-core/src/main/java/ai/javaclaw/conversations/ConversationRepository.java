package ai.javaclaw.conversations;

import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC repository for {@link Conversation} entities. */
public interface ConversationRepository extends ListCrudRepository<Conversation, String> {}
