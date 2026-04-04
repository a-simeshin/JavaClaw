package org.springframework.ai.chat.memory;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

public interface AppendableChatMemoryRepository extends ChatMemoryRepository {

    void appendAll(String conversationId, List<Message> messages);
}
