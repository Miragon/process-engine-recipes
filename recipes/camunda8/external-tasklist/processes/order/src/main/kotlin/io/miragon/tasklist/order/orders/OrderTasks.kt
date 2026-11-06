package io.miragon.tasklist.order.orders

import io.miragon.tasklist.contract.BadTasklistRequest
import io.miragon.tasklist.contract.TaskContext
import io.miragon.tasklist.contract.TaskState

/** The order a task works on: the process instance carries the order number as business ID. */
fun TaskContext.orderId(): String = businessId ?: throw BadTasklistRequest("The task names no order.")

/** Only the assignee works on an open task; everyone else sees its form read-only. */
val TaskContext.editable: Boolean
    get() = state == TaskState.OPEN && assignee == user.id

fun OrderRepository.findFor(context: TaskContext): Order =
    find(context.orderId()) ?: throw BadTasklistRequest("There is no order ${context.orderId()}.")

/** Loads the task's order and locks it, so a cancellation cannot slip between check and completion. */
fun OrderRepository.lockFor(context: TaskContext): Order =
    lock(context.orderId()) ?: throw BadTasklistRequest("There is no order ${context.orderId()}.")
