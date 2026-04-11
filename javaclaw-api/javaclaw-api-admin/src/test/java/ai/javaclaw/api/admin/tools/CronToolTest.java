package ai.javaclaw.api.admin.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ai.javaclaw.tasks.RecurringTask;
import ai.javaclaw.tasks.TaskManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CronToolTest {

    @Mock
    private TaskManager taskManager;

    private CronTool cronTool;

    @BeforeEach
    void setUp() {
        cronTool = new CronTool(taskManager);
    }

    @Test
    void validateCronValidExpression() {
        String result = cronTool.validateCron("0 9 * * *");
        assertThat(result).contains("Valid").contains("0 9 * * *").contains("daily");
    }

    @Test
    void validateCronInvalidExpression() {
        String result = cronTool.validateCron("invalid");
        assertThat(result).contains("Invalid");
    }

    @Test
    void validateCronBlankInput() {
        assertThat(cronTool.validateCron(null)).contains("Error");
        assertThat(cronTool.validateCron("")).contains("Error");
        assertThat(cronTool.validateCron("  ")).contains("Error");
    }

    @Test
    void getScheduleDetailsSuccess() {
        RecurringTask task =
                RecurringTask.newRecurringTask("daily-report", "Generate daily report", "0 9 * * *", "conv-1");
        when(taskManager.getRecurringTaskByName("daily-report")).thenReturn(task);

        String result = cronTool.getScheduleDetails("daily-report");

        assertThat(result)
                .contains("daily-report")
                .contains("0 9 * * *")
                .contains("Generate daily report")
                .contains("Active: yes")
                .contains("conv-1");
    }

    @Test
    void getScheduleDetailsNotFound() {
        when(taskManager.getRecurringTaskByName("nonexistent"))
                .thenThrow(new IllegalArgumentException("Recurring task with name 'nonexistent' was not found"));

        String result = cronTool.getScheduleDetails("nonexistent");
        assertThat(result).contains("Error").contains("not found");
    }

    @Test
    void getScheduleDetailsBlankName() {
        assertThat(cronTool.getScheduleDetails(null)).contains("Error");
        assertThat(cronTool.getScheduleDetails("")).contains("Error");
    }

    @Test
    void updateScheduleSuccess() {
        String result = cronTool.updateSchedule("weekly-cleanup", "0 0 * * MON");

        verify(taskManager).updateRecurringTaskCron("weekly-cleanup", "0 0 * * MON");
        assertThat(result).contains("updated").contains("weekly-cleanup").contains("0 0 * * MON");
    }

    @Test
    void updateScheduleBlankInputs() {
        assertThat(cronTool.updateSchedule(null, "0 9 * * *")).contains("Error");
        assertThat(cronTool.updateSchedule("task", null)).contains("Error");
        assertThat(cronTool.updateSchedule("", "0 9 * * *")).contains("Error");
        assertThat(cronTool.updateSchedule("task", "")).contains("Error");
        verifyNoInteractions(taskManager);
    }

    @Test
    void pauseScheduleSuccess() {
        String result = cronTool.pauseSchedule("daily-report");

        verify(taskManager).pauseRecurringTask("daily-report");
        assertThat(result).contains("paused").contains("daily-report");
    }

    @Test
    void pauseScheduleAlreadyPaused() {
        doThrow(new IllegalStateException("Recurring task 'daily-report' is already paused"))
                .when(taskManager)
                .pauseRecurringTask("daily-report");

        String result = cronTool.pauseSchedule("daily-report");
        assertThat(result).contains("Error").contains("already paused");
    }

    @Test
    void pauseScheduleBlankName() {
        assertThat(cronTool.pauseSchedule(null)).contains("Error");
        assertThat(cronTool.pauseSchedule("")).contains("Error");
        verifyNoInteractions(taskManager);
    }

    @Test
    void resumeScheduleSuccess() {
        String result = cronTool.resumeSchedule("daily-report");

        verify(taskManager).resumeRecurringTask("daily-report");
        assertThat(result).contains("resumed").contains("daily-report");
    }

    @Test
    void resumeScheduleAlreadyActive() {
        doThrow(new IllegalStateException("Recurring task 'daily-report' is already active"))
                .when(taskManager)
                .resumeRecurringTask("daily-report");

        String result = cronTool.resumeSchedule("daily-report");
        assertThat(result).contains("Error").contains("already active");
    }

    @Test
    void resumeScheduleBlankName() {
        assertThat(cronTool.resumeSchedule(null)).contains("Error");
        assertThat(cronTool.resumeSchedule("")).contains("Error");
        verifyNoInteractions(taskManager);
    }

    @Test
    void listSchedulesWithTasks() {
        List<RecurringTask> tasks = List.of(
                RecurringTask.newRecurringTask("daily-report", "Daily report generation", "0 9 * * *"),
                RecurringTask.newRecurringTask("weekly-cleanup", "Weekly log cleanup", "0 0 * * MON")
                        .withActive(false));
        when(taskManager.getAllRecurringTasks()).thenReturn(tasks);

        String result = cronTool.listSchedules();

        assertThat(result)
                .contains("2")
                .contains("daily-report")
                .contains("ACTIVE")
                .contains("weekly-cleanup")
                .contains("PAUSED")
                .contains("0 9 * * *")
                .contains("0 0 * * MON");
    }

    @Test
    void listSchedulesEmpty() {
        when(taskManager.getAllRecurringTasks()).thenReturn(List.of());

        String result = cronTool.listSchedules();
        assertThat(result).contains("No recurring tasks found");
    }

    @Test
    void describeCronEveryMinute() {
        assertThat(CronTool.describeCronExpression("* * * * *")).isEqualTo("every minute");
    }

    @Test
    void describeCronDailyAtTime() {
        String desc = CronTool.describeCronExpression("30 14 * * *");
        assertThat(desc).contains("14:30").contains("daily");
    }

    @Test
    void describeCronWeekly() {
        String desc = CronTool.describeCronExpression("0 9 * * MON");
        assertThat(desc).contains("9:00").contains("Monday");
    }

    @Test
    void describeCronInvalidFieldCount() {
        try {
            CronTool.describeCronExpression("* *");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("expected 5 or 6 fields");
        }
    }
}
