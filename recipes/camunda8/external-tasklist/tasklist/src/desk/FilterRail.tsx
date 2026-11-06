import { useApplications, useFacets } from '../api/queries';
import type { Facet } from '../api/types';
import { classes } from '../ui/tones';
import type { Assignment, Filters, StateFilter } from './location';

interface RailOption {
  value: string;
  label: string;
  count: number | undefined;
}

const STATE_OPTIONS: { value: StateFilter; label: string; facet: string }[] = [
  { value: 'open', label: 'Open', facet: 'OPEN' },
  { value: 'completed', label: 'Completed', facet: 'COMPLETED' },
  { value: 'canceled', label: 'Canceled', facet: 'CANCELED' },
];

/** The filters of the list with their counts. Domain and group toggle; assignment and state always have one choice. */
export function FilterRail({ filters, onChange }: { filters: Filters; onChange: (filters: Filters) => void }) {
  const { data: facets } = useFacets(filters.state);
  const { data: applications = [] } = useApplications();

  const assigned = (value: string) => facets?.assignments.find((facet) => facet.value === value)?.count;
  const assignments: RailOption[] = [
    { value: 'all', label: 'All tasks', count: assigned('ANY') },
    { value: 'mine', label: 'Mine', count: assigned('MINE') },
    { value: 'unassigned', label: 'Unassigned', count: assigned('UNASSIGNED') },
  ];
  const domains = withSelected(
    [
      ...applications.map((application) => ({
        value: application.domain,
        label: application.label,
        count: facets ? (facets.domains.find((facet) => facet.value === application.domain)?.count ?? 0) : undefined,
      })),
      ...(facets?.domains.filter((facet) => !applications.some((application) => application.domain === facet.value)).map(option) ?? []),
    ],
    filters.domain,
  );
  const states = STATE_OPTIONS.map(({ value, label, facet }) => ({
    value,
    label,
    count: facets ? (facets.states.find((candidate) => candidate.value === facet)?.count ?? 0) : undefined,
  }));
  const groups = withSelected(facets?.candidateGroups.map(option) ?? [], filters.group);

  return (
    <nav className="rail" aria-label="Filters">
      <RailSection
        title="Assignment"
        options={assignments}
        selected={filters.assignment}
        onSelect={(value) => {
          onChange({ ...filters, assignment: value as Assignment });
        }}
      />
      <RailSection
        title="Domain"
        options={domains}
        selected={filters.domain}
        onSelect={(value) => {
          onChange({ ...filters, domain: value === filters.domain ? null : value });
        }}
      />
      <RailSection
        title="State"
        options={states}
        selected={filters.state}
        onSelect={(value) => {
          onChange({ ...filters, state: value as StateFilter });
        }}
      />
      {groups.length > 0 && (
        <RailSection
          title="Candidate group"
          options={groups}
          selected={filters.group}
          onSelect={(value) => {
            onChange({ ...filters, group: value === filters.group ? null : value });
          }}
        />
      )}
    </nav>
  );
}

function option(facet: Facet): RailOption {
  return { value: facet.value, label: facet.label, count: facet.count };
}

/** Keeps the chosen value in the rail even when no task has it right now, so it can be cleared again. */
function withSelected(options: RailOption[], selected: string | null): RailOption[] {
  if (selected === null || options.some((candidate) => candidate.value === selected)) return options;
  return [...options, { value: selected, label: selected, count: 0 }];
}

interface RailSectionProps {
  title: string;
  options: RailOption[];
  selected: string | null;
  onSelect: (value: string) => void;
}

function RailSection({ title, options, selected, onSelect }: RailSectionProps) {
  const id = `rail-${title.toLowerCase().replaceAll(' ', '-')}`;
  return (
    <section className="rail__section" aria-labelledby={id}>
      <h3 className="rail__title" id={id}>
        {title}
      </h3>
      <ul className="rail__options">
        {options.map((candidate) => {
          const isSelected = candidate.value === selected;
          return (
            <li key={candidate.value}>
              <button
                type="button"
                className={classes('rail__option', isSelected && 'is-selected')}
                aria-pressed={isSelected}
                onClick={() => {
                  onSelect(candidate.value);
                }}
              >
                <span className="rail__label">{candidate.label}</span>
                {candidate.count !== undefined && <span className="count">{candidate.count}</span>}
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
