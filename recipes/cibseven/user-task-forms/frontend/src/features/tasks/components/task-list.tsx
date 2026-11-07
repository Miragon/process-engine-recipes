import { useOpenTasks, type OpenTask } from "../api/tasks";
import { resolveForm } from "../forms/form-registry";

export function TaskList() {
  const openTasks = useOpenTasks();

  if (openTasks.isPending) {
    return <p className="text-body">Loading open tasks…</p>;
  }
  if (openTasks.isError) {
    return (
      <p role="alert" className="field-error">
        The workshop service is not reachable.
      </p>
    );
  }
  if (openTasks.data.length === 0) {
    return <p className="text-body">No open tasks. Start a repair in the CIB seven web client.</p>;
  }

  return (
    <ul className="flex flex-col gap-4">
      {openTasks.data.map((task) => (
        <li key={task.id}>
          <TaskCard task={task} />
        </li>
      ))}
    </ul>
  );
}

function TaskCard({ task }: { task: OpenTask }) {
  const TaskForm = resolveForm(task);
  return (
    <section className="card" aria-label={task.name}>
      <h2 className="text-h3">{task.name}</h2>
      {TaskForm ? (
        <TaskForm task={task} />
      ) : (
        <p className="text-body text-schwarz/60">Handled in the CIB seven Tasklist.</p>
      )}
    </section>
  );
}
