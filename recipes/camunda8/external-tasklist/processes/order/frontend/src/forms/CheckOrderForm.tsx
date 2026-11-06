import type { FieldError as Problem } from '@tasklist/form-protocol';
import { Check, X } from 'lucide-react';
import { useState } from 'react';
import { clearResult, submit } from '../tasklist';
import { Button } from '../ui/Button';
import { TextAreaField } from '../ui/Field';
import { Icon } from '../ui/Icon';
import { ItemsTable } from '../ui/ItemsTable';
import { Notice } from '../ui/Notice';
import { StatusBadge } from '../ui/StatusBadge';
import type { FormProps } from './FormProps';
import { checkOrderSubmission, decisionErrors, messagesFor, type CheckOrderData, type OrderStatus } from './order';

/** `check-order@1`: sales approves the order, or declines it with a reason. */
export function CheckOrderForm({ data, readOnly, pending, result }: FormProps<CheckOrderData>) {
  const { order } = data;
  const [comment, setComment] = useState('');
  const [problems, setProblems] = useState<Problem[]>([]);
  const [approved, setApproved] = useState<boolean | null>(null);

  const decided = result?.ok === true;
  const locked = readOnly || pending || decided;
  const rejected = result !== null && !result.ok ? result : null;
  const errors = [...problems, ...(rejected?.errors ?? [])];

  const decide = (approve: boolean) => {
    const found = decisionErrors(approve, comment);
    setProblems(found);
    if (found.length > 0) return;
    setApproved(approve);
    submit(checkOrderSubmission(approve, comment));
  };

  return (
    <div className="form">
      <header className="form__header">
        <div>
          <h1 className="form__title">Order {order.id}</h1>
          <p className="form__lead">Check the order before MiraVelo asks the customer to pay.</p>
        </div>
        <StatusBadge status={order.status} />
      </header>

      <section className="section" aria-labelledby="customer-title">
        <h2 className="section__title" id="customer-title">
          Customer
        </h2>
        <dl className="details">
          <dt>Name</dt>
          <dd>{order.customer.name}</dd>
          <dt>Email</dt>
          <dd>{order.customer.email}</dd>
          <dt>Delivery address</dt>
          <dd>{order.customer.address}</dd>
        </dl>
      </section>

      <section className="section" aria-labelledby="items-title">
        <h2 className="section__title" id="items-title">
          Items
        </h2>
        <ItemsTable lines={order.items} total={order.total} currency={order.currency} />
      </section>

      <section className="section" aria-labelledby="decision-title">
        <h2 className="section__title" id="decision-title">
          Decision
        </h2>
        {readOnly ? (
          <ReadOnlyDecision status={order.status} comment={order.checkComment} />
        ) : (
          <>
            <TextAreaField
              label="Comment"
              hint="Required when you decline the order."
              rows={3}
              maxLength={1000}
              value={comment}
              error={messagesFor(errors, 'comment')}
              disabled={locked}
              onChange={(value) => {
                clearResult();
                setProblems([]);
                setComment(value);
              }}
            />
            {rejected && <Notice tone="danger" title={rejected.message ?? 'The decision was not saved.'} />}
            {decided && (
              <Notice
                tone="success"
                title={approved === true ? 'Order approved. The customer is asked to pay next.' : 'Order declined.'}
              />
            )}
            <div className="actions">
              <Button
                disabled={locked}
                onClick={() => {
                  decide(false);
                }}
              >
                <Icon of={X} />
                Decline
              </Button>
              <Button
                variant="primary"
                disabled={locked}
                onClick={() => {
                  decide(true);
                }}
              >
                <Icon of={Check} />
                {pending ? 'Saving…' : 'Approve'}
              </Button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function ReadOnlyDecision({ status, comment }: { status: OrderStatus; comment: string | null }) {
  if (status === 'CHECKING') {
    return <Notice tone="info" title="Assign the task to yourself to approve or decline the order." />;
  }
  if (status === 'CANCELED') {
    return <p className="muted">The customer canceled the order before sales decided.</p>;
  }
  return (
    <>
      <p>{status === 'DECLINED' ? 'Sales declined the order.' : 'Sales approved the order.'}</p>
      {comment !== null && <blockquote className="quote">{comment}</blockquote>}
    </>
  );
}
