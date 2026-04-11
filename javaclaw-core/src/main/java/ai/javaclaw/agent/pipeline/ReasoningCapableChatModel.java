package ai.javaclaw.agent.pipeline;

/**
 * Маркерный интерфейс для ChatModel-имплементаций, поддерживающих reasoning (thinking) токены.
 * Используется ChatServiceConfiguration для приоритизации reasoning-capable модели.
 */
public interface ReasoningCapableChatModel {}
