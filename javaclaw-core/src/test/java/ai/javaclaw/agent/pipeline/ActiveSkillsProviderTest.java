package ai.javaclaw.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ai.javaclaw.skills.Skill;
import ai.javaclaw.skills.SkillRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-тесты для {@link ActiveSkillsProvider}.
 *
 * <p>Проверяют: форматирование навыков, пустой список → пустая строка, null content → пропуск,
 * несколько навыков конкатенируются.
 */
@ExtendWith(MockitoExtension.class)
class ActiveSkillsProviderTest {

    /** Мок репозитория навыков. */
    @Mock
    private SkillRepository skillRepository;

    /** Тестируемый провайдер навыков. */
    private ActiveSkillsProvider activeSkillsProvider;

    @BeforeEach
    void setUp() {
        activeSkillsProvider = new ActiveSkillsProvider(skillRepository);
    }

    /**
     * Создаёт тестовый Skill с заданными name и content.
     *
     * @param name имя навыка
     * @param content содержимое навыка
     * @return экземпляр Skill
     */
    private Skill skill(final String name, final String content) {
        return new Skill("id-" + name, null, name, "desc", content, true, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("loadActiveSkills(): навык форматируется как '## Skill: name\\ncontent\\n\\n'")
    void loadActiveSkills_singleSkillFormattedCorrectly() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue())
                .thenReturn(List.of(skill("MySkill", "Do something useful.")));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).isEqualTo("## Skill: MySkill\nDo something useful.\n\n");
    }

    @Test
    @DisplayName("loadActiveSkills(): пустой список навыков → пустая строка")
    void loadActiveSkills_emptyList_returnsEmptyString() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of());

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("loadActiveSkills(): навык с null content — пропускается")
    void loadActiveSkills_nullContent_skipped() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of(skill("NullSkill", null)));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("loadActiveSkills(): навык с blank content — пропускается")
    void loadActiveSkills_blankContent_skipped() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenReturn(List.of(skill("BlankSkill", "   ")));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("loadActiveSkills(): несколько навыков конкатенируются")
    void loadActiveSkills_multipleSkillsConcatenated() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue())
                .thenReturn(List.of(skill("SkillA", "Content A"), skill("SkillB", "Content B")));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).contains("## Skill: SkillA\nContent A\n\n");
        assertThat(result).contains("## Skill: SkillB\nContent B\n\n");
        // SkillA должен идти перед SkillB
        assertThat(result.indexOf("SkillA")).isLessThan(result.indexOf("SkillB"));
    }

    @Test
    @DisplayName("loadActiveSkills(): смесь валидных и null-content навыков — null пропускаются")
    void loadActiveSkills_mixedNullAndValid_nullSkipped() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue())
                .thenReturn(List.of(skill("GoodSkill", "Valid content"), skill("BadSkill", null)));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).contains("## Skill: GoodSkill\nValid content\n\n");
        assertThat(result).doesNotContain("BadSkill");
    }

    @Test
    @DisplayName("loadActiveSkills(): исключение в репозитории → возвращается пустая строка")
    void loadActiveSkills_repositoryThrows_returnsEmptyString() {
        when(skillRepository.findAllByOwnerIdIsNullAndEnabledTrue()).thenThrow(new RuntimeException("DB error"));

        final String result = activeSkillsProvider.loadActiveSkills();

        assertThat(result).isEmpty();
    }
}
