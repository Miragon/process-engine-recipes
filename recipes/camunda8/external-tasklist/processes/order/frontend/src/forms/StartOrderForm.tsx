import type { FieldError as Problem } from '@tasklist/form-protocol';
import { ArrowLeft, ArrowRight, Check, Minus, Plus } from 'lucide-react';
import { useState } from 'react';
import { clearResult, submit } from '../tasklist';
import { Button } from '../ui/Button';
import { FieldError, TextAreaField, TextField } from '../ui/Field';
import { Icon } from '../ui/Icon';
import { ItemsTable } from '../ui/ItemsTable';
import { Notice } from '../ui/Notice';
import type { FormProps } from './FormProps';
import {
  chosenLines,
  customerErrors,
  formatMoney,
  itemErrors,
  MAX_LENGTH,
  MAX_QUANTITY,
  messagesFor,
  newOrderSubmission,
  totalOf,
  type CatalogItem,
  type Customer,
  type Quantities,
  type StartOrderData,
} from './order';

const STEPS = ['Items', 'Customer', 'Review'] as const;

/** `start-order@1`: places an order for a customer in three steps. */
export function StartOrderForm({ data, readOnly, pending, result }: FormProps<StartOrderData>) {
  const [step, setStep] = useState(0);
  const [quantities, setQuantities] = useState<Quantities>({});
  const [customer, setCustomer] = useState<Customer>({ name: '', email: '', address: '' });
  const [problems, setProblems] = useState<Problem[]>([]);

  const placed = result?.ok === true;
  const locked = readOnly || pending || placed;
  const rejected = result !== null && !result.ok ? result : null;
  const errors = [...problems, ...(rejected?.errors ?? [])];
  const lines = chosenLines(data.items, quantities);

  const goTo = (target: number) => {
    setProblems([]);
    setStep(target);
  };
  const next = () => {
    const found = step === 0 ? itemErrors(quantities) : customerErrors(customer);
    setProblems(found);
    if (found.length === 0) setStep(step + 1);
  };
  const changeQuantity = (itemId: string, quantity: number) => {
    clearResult();
    setProblems((current) => current.filter((problem) => problem.field !== 'items'));
    setQuantities({ ...quantities, [itemId]: quantity });
  };
  const changeCustomer = (field: keyof Customer, value: string) => {
    clearResult();
    setProblems((current) => current.filter((problem) => problem.field !== `customer.${field}`));
    setCustomer({ ...customer, [field]: value });
  };

  return (
    <div className="form">
      <header className="form__header">
        <div>
          <h1 className="form__title">New order</h1>
          <p className="form__lead">Place an order for a MiraVelo customer.</p>
        </div>
      </header>

      <ol className="stepper" aria-label="Steps">
        {STEPS.map((label, index) => (
          <li
            key={label}
            className={`stepper__step${index < step ? ' stepper__step--done' : ''}`}
            aria-current={index === step ? 'step' : undefined}
          >
            <span className="stepper__number">{index < step ? <Icon of={Check} size={12} /> : index + 1}</span>
            {label}
          </li>
        ))}
      </ol>

      {step === 0 && (
        <section className="section" aria-labelledby="items-title">
          <h2 className="section__title" id="items-title">
            Choose items
          </h2>
          <ul className="catalog">
            {data.items.map((item) => (
              <CatalogRow
                key={item.id}
                item={item}
                currency={data.currency}
                quantity={quantities[item.id] ?? 0}
                disabled={locked}
                onChange={(quantity) => {
                  changeQuantity(item.id, quantity);
                }}
              />
            ))}
          </ul>
          <FieldError message={messagesFor(errors, 'items')} />
          <p className="total">
            <span>Total</span>
            <strong>{formatMoney(totalOf(lines), data.currency)}</strong>
          </p>
          <div className="actions">
            <Button variant="primary" onClick={next} disabled={locked}>
              Next
              <Icon of={ArrowRight} />
            </Button>
          </div>
        </section>
      )}

      {step === 1 && (
        <section className="section" aria-labelledby="customer-title">
          <h2 className="section__title" id="customer-title">
            Customer details
          </h2>
          <div className="fields">
            <TextField
              label="Name"
              autoComplete="name"
              maxLength={MAX_LENGTH.name}
              value={customer.name}
              error={messagesFor(errors, 'customer.name')}
              disabled={locked}
              onChange={(value) => {
                changeCustomer('name', value);
              }}
            />
            <TextField
              label="Email"
              type="email"
              autoComplete="email"
              maxLength={MAX_LENGTH.email}
              value={customer.email}
              error={messagesFor(errors, 'customer.email')}
              disabled={locked}
              onChange={(value) => {
                changeCustomer('email', value);
              }}
            />
            <TextAreaField
              label="Delivery address"
              autoComplete="street-address"
              rows={3}
              maxLength={MAX_LENGTH.address}
              value={customer.address}
              error={messagesFor(errors, 'customer.address')}
              disabled={locked}
              onChange={(value) => {
                changeCustomer('address', value);
              }}
            />
          </div>
          <div className="actions actions--split">
            <Button
              onClick={() => {
                goTo(0);
              }}
              disabled={locked}
            >
              <Icon of={ArrowLeft} />
              Back
            </Button>
            <Button variant="primary" onClick={next} disabled={locked}>
              Next
              <Icon of={ArrowRight} />
            </Button>
          </div>
        </section>
      )}

      {step === 2 && (
        <section className="section" aria-labelledby="review-title">
          <h2 className="section__title" id="review-title">
            Check and place the order
          </h2>
          <ItemsTable lines={lines} total={totalOf(lines)} currency={data.currency} />
          <dl className="details">
            <dt>Name</dt>
            <dd>{customer.name.trim()}</dd>
            <dt>Email</dt>
            <dd>{customer.email.trim()}</dd>
            <dt>Delivery address</dt>
            <dd>{customer.address.trim()}</dd>
          </dl>
          {rejected && (
            <Notice tone="danger" title={rejected.message ?? 'The order was not placed.'}>
              {(rejected.errors ?? []).length > 0 && (
                <ul>
                  {rejected.errors?.map((error) => (
                    <li key={`${error.field}:${error.message}`}>{error.message}</li>
                  ))}
                </ul>
              )}
              <div className="notice__actions">
                <Button
                  variant="ghost"
                  onClick={() => {
                    goTo(0);
                  }}
                >
                  Edit items
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => {
                    goTo(1);
                  }}
                >
                  Edit customer
                </Button>
              </div>
            </Notice>
          )}
          {placed && <Notice tone="success" title="Order placed. Sales checks it next." />}
          <div className="actions actions--split">
            <Button
              onClick={() => {
                goTo(1);
              }}
              disabled={locked}
            >
              <Icon of={ArrowLeft} />
              Back
            </Button>
            <Button
              variant="primary"
              disabled={locked}
              onClick={() => {
                submit(newOrderSubmission(data.items, customer, quantities));
              }}
            >
              <Icon of={Check} />
              {pending ? 'Placing order…' : 'Place order'}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}

interface CatalogRowProps {
  item: CatalogItem;
  currency: string;
  quantity: number;
  disabled: boolean;
  onChange: (quantity: number) => void;
}

function CatalogRow({ item, currency, quantity, disabled, onChange }: CatalogRowProps) {
  return (
    <li className={`catalog__item${quantity > 0 ? ' catalog__item--chosen' : ''}`}>
      <div>
        <p className="catalog__name">{item.name}</p>
        <p className="catalog__description">{item.description}</p>
      </div>
      <span className="catalog__price">{formatMoney(item.price, currency)}</span>
      <div className="quantity" role="group" aria-label={`Quantity of ${item.name}`}>
        <button
          type="button"
          className="quantity__button"
          aria-label={`One ${item.name} less`}
          disabled={disabled || quantity === 0}
          onClick={() => {
            onChange(quantity - 1);
          }}
        >
          <Icon of={Minus} />
        </button>
        <output className="quantity__value" aria-live="polite">
          {quantity}
        </output>
        <button
          type="button"
          className="quantity__button"
          aria-label={`One ${item.name} more`}
          disabled={disabled || quantity === MAX_QUANTITY}
          onClick={() => {
            onChange(quantity + 1);
          }}
        >
          <Icon of={Plus} />
        </button>
      </div>
    </li>
  );
}
