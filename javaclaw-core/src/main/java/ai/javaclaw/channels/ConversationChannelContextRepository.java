package ai.javaclaw.channels;

import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository for {@link ConversationChannelContext}. */
public interface ConversationChannelContextRepository extends CrudRepository<ConversationChannelContext, String> {}
