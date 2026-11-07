import type { LucideIcon } from 'lucide-react';

/** A Lucide icon in the Miragon style: outline, stroke 2.5 on the 24 grid, colored by the text around it. */
export function Icon({ of: Glyph, size = 16, className }: { of: LucideIcon; size?: number; className?: string }) {
  return <Glyph size={size} strokeWidth={2.5} aria-hidden="true" focusable="false" className={className} />;
}
