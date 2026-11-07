package io.miragon.miravelo.workshop;

import org.cibseven.bpm.engine.task.Task;

public record OpenTask(String id, String name, String taskDefinitionKey, String formKey) {

    static OpenTask of(Task task) {
        return new OpenTask(task.getId(), task.getName(), task.getTaskDefinitionKey(), task.getFormKey());
    }
}
