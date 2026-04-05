package ai.javaclaw.api.admin.tools;

import ai.javaclaw.api.admin.files.VirtualFileService;
import ai.javaclaw.skills.SkillRepository;
import ai.javaclaw.tools.AutoDiscoveredTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers DB-backed agent tools as {@link AutoDiscoveredTool} beans so that
 * {@code JavaClawConfiguration} picks them up automatically via the
 * {@code Set<AutoDiscoveredTool<?>>} injection point.
 */
@Configuration
public class AgentToolsConfiguration {

    @Bean
    public AutoDiscoveredTool<FileOperationsTool> fileOperationsTool(final VirtualFileService fileService) {
        return new AutoDiscoveredTool<>(
                FileOperationsTool.builder().fileService(fileService).build());
    }

    @Bean
    public AutoDiscoveredTool<SkillsTool> skillsTool(final SkillRepository skillRepository) {
        return new AutoDiscoveredTool<>(
                SkillsTool.builder().skillRepository(skillRepository).build());
    }
}
