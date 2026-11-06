// JSON pointers (RFC 6901), with which JSON Forms and JSON Schema name the fields of a form.

/** `['items', '0', 'a/b']` as `/items/0/a~1b`. */
export function toPointer(segments: readonly string[]): string {
  return segments.map((segment) => `/${segment.replaceAll('~', '~0').replaceAll('/', '~1')}`).join('');
}

/** `/items/0/a~1b` as `['items', '0', 'a/b']`. A URI fragment, as JSON Schema output writes them (`#/a%20b`), is decoded too. */
export function fromPointer(pointer: string): string[] {
  const fragment = pointer.startsWith('#');
  return (fragment ? pointer.slice(1) : pointer)
    .split('/')
    .slice(1)
    .map((segment) => (fragment ? decodeURIComponent(segment) : segment).replaceAll('~1', '/').replaceAll('~0', '~'));
}
