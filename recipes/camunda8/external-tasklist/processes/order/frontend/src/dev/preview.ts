// A stand-in for the tasklist during `npm run dev`. The production build leaves it out.
// Open /?form=check-order@1, add &readOnly to see the read-only form or &result=error to see a refused submission.
import { envelope, type InitPayload, type ResultPayload } from '@tasklist/form-protocol';

const samples: Record<string, InitPayload> = {
  'start-order@1': {
    formRef: 'start-order@1',
    mode: 'start',
    readOnly: false,
    draftable: false,
    data: {
      currency: 'EUR',
      items: [
        { id: 'city-helmet', name: 'City helmet', description: 'Lightweight helmet with an integrated rear light, size M', price: 79.9 },
        { id: 'floor-pump', name: 'Floor pump', description: 'Floor pump with pressure gauge for all valve types', price: 29.9 },
        { id: 'folding-lock', name: 'Folding lock', description: 'Hardened steel folding lock with frame mount', price: 50 },
        { id: 'inner-tube', name: 'Inner tube 28"', description: 'Inner tube 28 x 1.5-2.1 inch with Presta valve', price: 7.5 },
        { id: 'light-set', name: 'LED light set', description: 'Front and rear light, rechargeable over USB-C', price: 34.9 },
        { id: 'pannier-bag', name: 'Pannier bag', description: 'Waterproof 20 litre bag for the rear rack', price: 59 },
      ],
    },
  },
  'check-order@1': {
    formRef: 'check-order@1',
    mode: 'task',
    readOnly: false,
    draftable: false,
    data: {
      order: {
        id: 'ORD-1001',
        status: 'CHECKING',
        customer: { name: 'Alice Smith', email: 'alice@example.com', address: 'Hauptstraße 1\n80331 München' },
        items: [
          { name: 'City helmet', quantity: 1, price: 79.9 },
          { name: 'Folding lock', quantity: 1, price: 50 },
        ],
        total: 129.9,
        currency: 'EUR',
        checkComment: null,
      },
    },
  },
};

const params = new URLSearchParams(window.location.search);
const formRef = params.get('form') ?? 'start-order@1';
const sample = samples[formRef];

// How the order application refuses a submission of each form.
const refusals: Partial<Record<string, ResultPayload>> = {
  'start-order@1': {
    ok: false,
    message: 'The order is not complete. Check the marked fields.',
    errors: [{ field: 'customer.email', message: 'Enter a valid email address.' }],
  },
  'check-order@1': { ok: false, message: 'The task is no longer active.' },
};

window.addEventListener('message', (event: MessageEvent<{ type?: string; payload?: unknown }>) => {
  if (event.data.type !== 'submit') return;
  console.info('The form submitted', event.data.payload);
  const result = (params.get('result') === 'error' ? refusals[formRef] : undefined) ?? { ok: true };
  window.setTimeout(() => {
    window.postMessage(envelope({ type: 'result', payload: result }), '*');
  }, 600);
});

if (sample !== undefined) {
  window.postMessage(envelope({ type: 'init', payload: { ...sample, readOnly: params.has('readOnly') } }), '*');
}
