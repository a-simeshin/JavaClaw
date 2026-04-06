package ai.javaclaw.agent.pipeline;

import ai.javaclaw.agent.SystemPromptProvider;
import ai.javaclaw.tools.AgentEnvironment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Assembles an {@link AssembledPrompt} from individual prompt sections.
 *
 * <p>The system message is built from four sections (all non-blank sections joined with {@code
 * "\n\n"}):
 * <ol>
 *   <li>Identity — AGENT.md + SOUL.md from {@link SystemPromptProvider#loadIdentity()}</li>
 *   <li>Skills — globally enabled skills from {@link ActiveSkillsProvider#loadActiveSkills()}</li>
 *   <li>Context — INFO.md from {@link SystemPromptProvider#loadContext()}</li>
 *   <li>Environment — {@link AgentEnvironment#info()} (if not blank)</li>
 * </ol>
 *
 * <p>History is loaded directly from {@link ChatMemoryRepository} (bypassing the window applied
 * by {@link ChatMemory}), filtered to remove {@link SystemMessage} instances, sanitized via
 * {@link MessageSanitizer}, and then windowed by token budget via
 * {@link TurnBoundaryWindower} before being included in the assembled prompt.
 *
 * <p>{@link ChatMemory} is still used exclusively for <em>writing</em> new messages so that the
 * append logic remains in one place.
 */
@Component
public class MessageAssembler {

    /** Provider for identity and context sections of the system prompt. */
    private final SystemPromptProvider systemPromptProvider;

    /** Provider for active skill sections of the system prompt. */
    private final ActiveSkillsProvider activeSkillsProvider;

    /**
     * Conversation history store used exclusively for <em>writing</em> new messages.
     * Reading is done via {@link #chatMemoryRepository} to obtain the full unwindowed history.
     */
    private final ChatMemory chatMemory;

    /**
     * Raw repository used for <em>reading</em> the full, unwindowed conversation history.
     * Bypassing {@link ChatMemory#get(String)} avoids the message-count window applied there
     * so that {@link TurnBoundaryWindower} can apply turn-aware windowing instead.
     */
    private final ChatMemoryRepository chatMemoryRepository;

    /** Sanitizer that cleans up malformed or redundant history messages. */
    private final MessageSanitizer messageSanitizer;

    /** Windower that trims history by whole conversation turns using token budget. */
    private final TurnBoundaryWindower windower;

    /** Token budget configuration properties for computing the available history window. */
    private final TokenBudgetProperties budgetProperties;

    /** Token estimator used to measure message costs in characters-to-tokens approximation. */
    private final TokenEstimator tokenEstimator;

    /**
     * Creates a {@code MessageAssembler} with all required dependencies.
     *
     * @param systemPromptProvider  provider of identity and context prompt sections, must not be null
     * @param activeSkillsProvider  provider of active skill prompt sections, must not be null
     * @param chatMemory            conversation history store used for writing, must not be null
     * @param chatMemoryRepository  raw repository used for reading full history, must not be null
     * @param messageSanitizer      sanitizer for history messages, must not be null
     * @param windower              turn-boundary windower for history trimming, must not be null
     * @param budgetProperties      token budget configuration, must not be null
     * @param tokenEstimator        token estimator for measuring message costs, must not be null
     */
    public MessageAssembler(
            final SystemPromptProvider systemPromptProvider,
            final ActiveSkillsProvider activeSkillsProvider,
            final ChatMemory chatMemory,
            final ChatMemoryRepository chatMemoryRepository,
            final MessageSanitizer messageSanitizer,
            final TurnBoundaryWindower windower,
            final TokenBudgetProperties budgetProperties,
            final TokenEstimator tokenEstimator) {
        Assert.notNull(systemPromptProvider, "systemPromptProvider must not be null");
        Assert.notNull(activeSkillsProvider, "activeSkillsProvider must not be null");
        Assert.notNull(chatMemory, "chatMemory must not be null");
        Assert.notNull(chatMemoryRepository, "chatMemoryRepository must not be null");
        Assert.notNull(messageSanitizer, "messageSanitizer must not be null");
        Assert.notNull(windower, "windower must not be null");
        Assert.notNull(budgetProperties, "budgetProperties must not be null");
        Assert.notNull(tokenEstimator, "tokenEstimator must not be null");
        this.systemPromptProvider = systemPromptProvider;
        this.activeSkillsProvider = activeSkillsProvider;
        this.chatMemory = chatMemory;
        this.chatMemoryRepository = chatMemoryRepository;
        this.messageSanitizer = messageSanitizer;
        this.windower = windower;
        this.budgetProperties = budgetProperties;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Assembles an {@link AssembledPrompt} for the given conversation and user input.
     *
     * <p>The system message is built by joining all non-blank sections with {@code "\n\n"}.
     * History is read from the raw repository, filtered to exclude any {@link SystemMessage}
     * instances, sanitized, and then windowed by token budget (system tokens estimated per
     * request) by whole turns.
     *
     * @param conversationId identifier of the conversation, must not be blank
     * @param userContent    content of the current user message, must not be blank
     * @return assembled prompt containing the system message, filtered history, and user message
     */
    public AssembledPrompt assemble(final String conversationId, final String userContent) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(userContent, "userContent must not be blank");

        final SystemMessage systemMessage = buildSystemMessage();
        final int systemTokens = tokenEstimator.estimate(systemMessage);
        final TokenBudget budget = budgetProperties.toBudget().withSystemTokens(systemTokens);

        final List<Message> rawHistory = loadFilteredHistory(conversationId);
        final List<Message> sanitizedHistory = messageSanitizer.sanitize(rawHistory);
        final int availableTokens = budget.availableForHistory();
        final List<Message> windowedHistory = sanitizedHistory.isEmpty() || availableTokens <= 0
                ? List.of()
                : windower.window(sanitizedHistory, availableTokens, tokenEstimator);

        return new AssembledPrompt(systemMessage, windowedHistory, new UserMessage(userContent));
    }

    /**
     * Builds the sectioned system message by joining all non-blank sections.
     *
     * @return system message containing all available prompt sections
     */
    private SystemMessage buildSystemMessage() {
        final List<String> sections = new ArrayList<>(4);

        final String identity = systemPromptProvider.loadIdentity();
        if (identity != null && !identity.isBlank()) {
            sections.add(identity);
        }

        final String skills = activeSkillsProvider.loadActiveSkills();
        if (skills != null && !skills.isBlank()) {
            sections.add(skills.strip());
        }

        final String context = systemPromptProvider.loadContext();
        if (context != null && !context.isBlank()) {
            sections.add(context);
        }

        final String envInfo = AgentEnvironment.info().toString();
        if (envInfo != null && !envInfo.isBlank()) {
            sections.add("# Environment\n" + envInfo);
        }

        final String combined = String.join("\n\n", sections);
        return new SystemMessage(combined);
    }

    /**
     * Loads conversation history directly from {@link ChatMemoryRepository} and filters out any
     * {@link SystemMessage} instances.
     *
     * <p>Using the raw repository instead of {@link ChatMemory#get(String)} ensures the full
     * history is available so {@link TurnBoundaryWindower} can apply turn-aware windowing.
     *
     * @param conversationId identifier of the conversation
     * @return mutable list of non-system messages from history
     */
    private List<Message> loadFilteredHistory(final String conversationId) {
        final List<Message> raw = chatMemoryRepository.findByConversationId(conversationId);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        final List<Message> filtered = new ArrayList<>(raw.size());
        for (final Message message : raw) {
            if (!(message instanceof SystemMessage)) {
                filtered.add(message);
            }
        }
        return filtered;
    }
}
