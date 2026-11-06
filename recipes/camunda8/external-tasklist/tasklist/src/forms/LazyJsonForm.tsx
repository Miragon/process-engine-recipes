import { lazy, Suspense, type ComponentProps } from 'react';

// JSON Forms and its validator are most of the bundle; they load with the first JSON form.
const Loaded = lazy(() => import('./JsonForm').then((module) => ({ default: module.JsonForm })));

export function JsonForm(props: ComponentProps<typeof Loaded>) {
  return (
    <Suspense
      fallback={
        <div className="form-skeleton" aria-busy="true" aria-label="Loading the form">
          <span className="skeleton skeleton--title" />
          <span className="skeleton skeleton--field" />
          <span className="skeleton skeleton--field" />
        </div>
      }
    >
      <Loaded {...props} />
    </Suspense>
  );
}
