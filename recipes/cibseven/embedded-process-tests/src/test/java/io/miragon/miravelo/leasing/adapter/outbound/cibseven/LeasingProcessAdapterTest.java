package io.miragon.miravelo.leasing.adapter.outbound.cibseven;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.task.Task;
import org.cibseven.bpm.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class LeasingProcessAdapterTest {

    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(runtimeService, taskService);

    @Test
    void startsTheProcessWithTheApplicationIdAsBusinessKey() {
        underTest.startLeasing("APP-1", 5000);

        verify(runtimeService).startProcessInstanceByKey(
                "bikeLeasingProcess", "APP-1", Map.of("contractValue", 5000.0));
    }

    @Test
    void approvesTheApplicationByCompletingItsApprovalTask() {
        openTask("task-1");

        underTest.approveApplication("APP-1");

        verify(taskQuery).processInstanceBusinessKey("APP-1");
        verify(taskQuery).taskDefinitionKey("Task_ApproveApplication");
        verify(taskService).complete("task-1");
    }

    @Test
    void preparesTheContractByCompletingItsTask() {
        openTask("task-2");

        underTest.prepareContract("APP-1");

        verify(taskQuery).processInstanceBusinessKey("APP-1");
        verify(taskQuery).taskDefinitionKey("Task_PrepareContract");
        verify(taskService).complete("task-2");
    }

    @Test
    void failsWhenTheApplicationHasNoOpenTask() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> underTest.prepareContract("APP-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Task_PrepareContract");

        verify(taskService, never()).complete(ArgumentMatchers.anyString());
    }

    private void openTask(String taskId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(taskId);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
    }
}
