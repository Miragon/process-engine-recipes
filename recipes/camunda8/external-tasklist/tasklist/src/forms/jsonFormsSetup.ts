// The vanilla renderer set of JSON Forms in the Miragon style. The vanilla cells draw the inputs; the style context
// gives them and the vanilla layouts the classes of forms.css. A few renderers of our own rank above vanilla ones.
import { and, isBooleanControl, isControl, isObjectArrayControl, rankWith, schemaMatches } from '@jsonforms/core';
import { withJsonFormsArrayControlProps, withJsonFormsControlProps } from '@jsonforms/react';
import { vanillaCells, vanillaRenderers, type StyleContext } from '@jsonforms/vanilla-renderers';
import { BooleanChoiceControl, InputControl, ItemsTableControl, ReadOnlyControl } from './jsonFormsRenderers';
import { isReadOnlySchema } from './readOnly';

/** The vanilla style IDs mapped to the classes of forms.css. */
export const FORM_STYLE_CONTEXT: StyleContext = {
  styles: [
    { name: 'control', classNames: ['jf-control'] },
    { name: 'control.label', classNames: ['jf-label'] },
    { name: 'control.input', classNames: ['jf-input'] },
    { name: 'control.select', classNames: ['jf-input', 'jf-select'] },
    { name: 'control.checkbox', classNames: ['jf-checkbox'] },
    { name: 'control.radio', classNames: ['jf-radios'] },
    { name: 'control.radio.option', classNames: ['jf-radios__option'] },
    { name: 'control.radio.input', classNames: ['jf-radio'] },
    { name: 'control.radio.label', classNames: ['jf-radios__label'] },
    { name: 'control.validation', classNames: ['jf-message'] },
    { name: 'control.validation.error', classNames: ['jf-message--error'] },
    { name: 'input.description', classNames: ['jf-message--description'] },
    { name: 'vertical.layout', classNames: ['jf-stack'] },
    { name: 'horizontal.layout', classNames: ['jf-row'] },
    { name: 'horizontal.layout.item', classNames: () => ['jf-row__item'] },
    { name: 'group.layout', classNames: ['jf-group'] },
    { name: 'group.label', classNames: ['jf-group__title'] },
    { name: 'group.layout.item', classNames: [] },
    { name: 'label-control', classNames: ['jf-text'] },
    { name: 'array.layout', classNames: ['jf-array'] },
    { name: 'array.children', classNames: ['jf-array__items'] },
    { name: 'array.button', classNames: ['btn', 'btn--secondary', 'btn--sm'] },
    { name: 'array.control', classNames: ['jf-array', 'jf-control'] },
    { name: 'array.control.add', classNames: ['btn', 'btn--secondary', 'btn--sm'] },
    { name: 'array.control.validation', classNames: ['jf-message'] },
    { name: 'array.control.validation.error', classNames: ['jf-message--error'] },
    { name: 'array.child.controls', classNames: ['jf-array__controls'] },
    { name: 'array.child.controls.up', classNames: ['btn', 'btn--ghost', 'btn--sm'] },
    { name: 'array.child.controls.down', classNames: ['btn', 'btn--ghost', 'btn--sm'] },
    { name: 'array.child.controls.delete', classNames: ['btn', 'btn--ghost', 'btn--sm'] },
  ],
};

export const FORM_RENDERERS = [
  ...vanillaRenderers,
  { tester: rankWith(2, isControl), renderer: withJsonFormsControlProps(InputControl) },
  { tester: rankWith(3, isBooleanControl), renderer: withJsonFormsControlProps(BooleanChoiceControl) },
  { tester: rankWith(5, isObjectArrayControl), renderer: withJsonFormsArrayControlProps(ItemsTableControl) },
  {
    tester: rankWith(6, and(isControl, schemaMatches((schema) => isReadOnlySchema(schema)))),
    renderer: withJsonFormsControlProps(ReadOnlyControl),
  },
];

export const FORM_CELLS = vanillaCells;
