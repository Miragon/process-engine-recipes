package io.miragon.tasklist.taskmanager.projection

/** The models of deployed process definitions. */
fun interface ProcessModels {

    /** Name and form reference of each user task of a definition by element ID; null while the engine does not know it yet. */
    fun userTasks(processDefinitionKey: Long, processId: String): Map<String, TaskDetails>?
}
