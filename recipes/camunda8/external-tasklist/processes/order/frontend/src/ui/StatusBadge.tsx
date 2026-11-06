import type { OrderStatus } from '../forms/order';
import type { Tone } from './Notice';

const STATUS: Record<OrderStatus, { label: string; tone: Tone }> = {
  CHECKING: { label: 'Being checked', tone: 'info' },
  AWAITING_PAYMENT: { label: 'Awaiting payment', tone: 'warning' },
  PREPARING: { label: 'Preparing delivery', tone: 'info' },
  SHIPPED: { label: 'Shipped', tone: 'success' },
  DECLINED: { label: 'Declined', tone: 'danger' },
  CANCELED: { label: 'Canceled', tone: 'danger' },
  NOT_PAID: { label: 'Not paid', tone: 'danger' },
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  const { label, tone } = STATUS[status];
  return <span className={`badge badge--${tone}`}>{label}</span>;
}
