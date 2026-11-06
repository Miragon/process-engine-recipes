import type { UISchemaElement } from '@jsonforms/core';
import { JsonForms } from '@jsonforms/react';
import { JsonFormsStyleContext } from '@jsonforms/vanilla-renderers';
import type { ComponentProps } from 'react';
import type { FormData, JsonFormsView } from '../api/types';
import { translateError, type FormError } from './jsonFormsErrors';
import { FORM_CELLS, FORM_RENDERERS, FORM_STYLE_CONTEXT } from './jsonFormsSetup';
import { createSchemaValidator } from './schemaValidator';

const I18N = { locale: 'en', translateError };

/** Stands in for Ajv, whose generated code the content security policy forbids (see schemaValidator.ts). */
const VALIDATOR = createSchemaValidator() as unknown as NonNullable<ComponentProps<typeof JsonForms>['ajv']>;

interface JsonFormProps {
  form: JsonFormsView;
  data: FormData;
  readOnly: boolean;
  /** Errors JSON Forms shows at the controls; its own schema validation stays hidden (see visibleErrors). */
  errors: FormError[];
  onChange: (data: FormData, schemaErrors: FormError[]) => void;
}

/** A form of type `json-forms`, rendered by the tasklist with the vanilla renderers of JSON Forms. */
export function JsonForm({ form, data, readOnly, errors, onChange }: JsonFormProps) {
  return (
    <JsonFormsStyleContext value={FORM_STYLE_CONTEXT}>
      <div className="jf-form">
        <JsonForms
          schema={form.schema}
          uischema={(form.uiSchema ?? undefined) as UISchemaElement | undefined}
          data={data}
          renderers={FORM_RENDERERS}
          cells={FORM_CELLS}
          ajv={VALIDATOR}
          readonly={readOnly}
          validationMode="ValidateAndHide"
          additionalErrors={errors}
          i18n={I18N}
          onChange={(state) => {
            onChange((state.data ?? {}) as FormData, state.errors ?? []);
          }}
        />
      </div>
    </JsonFormsStyleContext>
  );
}
