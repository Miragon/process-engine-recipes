# History

This folder keeps the material the recipe started from: the master's thesis *Task-Management in einer Microservice Umgebung* (TH Augsburg, 2024, written with Miragon), the slides of its defence and the original architecture diagrams.

**It is outdated.** It describes the first version of this recipe on Camunda 8.5: job-worker-based user tasks polled by the task manager, an optional custom Zeebe exporter, Kafka between the process applications and forms loaded by the browser directly from each application. The current recipe replaces all of that with Camunda user tasks, a global user task listener, REST between the services and a task manager that acts as the backend of the tasklist. Read the [recipe README](../../README.md) and [architecture notes](../architecture.md) for how it works today.

| File | Content |
| --- | --- |
| `masterarbeit_final.pdf` | The thesis (German) |
| `presentation.pptx` | Slides of the thesis defence (German) |
| `images/*.excalidraw.png` | Overview, task manager and process application diagrams of the original design |
