package io.miragon.tasklist.taskmanager.api

import io.miragon.tasklist.contract.UserContext
import io.miragon.tasklist.taskmanager.applications.Applications
import io.miragon.tasklist.taskmanager.identity.DemoUserView
import io.miragon.tasklist.taskmanager.identity.DemoUsers
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MetaController(
    private val applications: Applications,
    private val users: DemoUsers,
) {
    @GetMapping("/applications")
    fun applications(): List<ApplicationDto> = applications.all().map { ApplicationDto(it.domain, it.label) }

    @GetMapping("/users")
    fun users(): List<DemoUserView> = users.all()

    @GetMapping("/me")
    fun me(user: UserContext): DemoUserView = DemoUserView(user.id, users.nameOf(user.id), user.groups)
}
