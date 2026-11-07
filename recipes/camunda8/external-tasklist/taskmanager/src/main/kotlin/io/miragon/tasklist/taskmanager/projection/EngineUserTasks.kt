package io.miragon.tasklist.taskmanager.projection

import io.miragon.tasklist.contract.TaskState
import java.time.OffsetDateTime

/** What the engine's search knows about user tasks. It lags behind the listener. */
interface EngineUserTasks {

    /** The task as the search knows it, or null if it does not know it (yet). */
    fun find(userTaskKey: Long): EngineUserTask?

    /** Open tasks created before [createdBefore], oldest first, one page at a time. */
    fun openCreatedBefore(createdBefore: OffsetDateTime, from: Int, limit: Int): List<CamundaTask>
}

/** A user task in the engine's search: its state and what the projection may lack. */
data class EngineUserTask(val state: TaskState, val details: TaskDetails)
