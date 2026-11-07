import { TaskList } from "../features/tasks/components/task-list";

export function App() {
  return (
    <main className="mx-auto flex max-w-page flex-col gap-8 px-6 py-10">
      <header className="flex flex-col gap-2">
        <span className="h-1 w-16 rounded-md bg-gruen" />
        <h1 className="text-h1">Bike repair tasks</h1>
        <p className="text-body text-schwarz/60">
          The open repair tasks this app renders itself. Everything else runs in the CIB seven
          Tasklist.
        </p>
      </header>
      <TaskList />
    </main>
  );
}
