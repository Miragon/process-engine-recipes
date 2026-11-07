import { CircleCheck, CircleX, Info, TriangleAlert, type LucideIcon } from 'lucide-react';

/** The four status roles of the design system: `--cd-ui-<tone>` and `--cd-ui-<tone>-flaeche`. */
export type Tone = 'info' | 'success' | 'warning' | 'danger';

export const TONE_ICONS: Record<Tone, LucideIcon> = {
  info: Info,
  success: CircleCheck,
  warning: TriangleAlert,
  danger: CircleX,
};

export function classes(...names: (string | false | null | undefined)[]): string {
  return names.filter(Boolean).join(' ');
}
