import { RULEBOOK_PRESETS, presetText } from './presets';

interface Props {
  /** Nombre de la sección; también prefija las etiquetas para distinguir varios editores en la página. */
  title: string;
  value: string;
  onChange: (value: string) => void;
}

/** Elige un reglamento de ejemplo y lo deja editable como JSON del contrato de la API. */
export default function RulebookEditor({ title, value, onChange }: Props) {
  return (
    <fieldset>
      <legend>{title}</legend>
      <label>
        {`${title}: ejemplo`}
        <select defaultValue="" onChange={(event) => event.target.value && onChange(presetText(event.target.value))}>
          <option value="" disabled>
            Elegí un ejemplo para cargarlo…
          </option>
          {RULEBOOK_PRESETS.map((preset) => (
            <option key={preset.id} value={preset.id}>
              {preset.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        {`${title} (JSON)`}
        <textarea className="json-editor" rows={12} spellCheck={false} value={value}
          onChange={(event) => onChange(event.target.value)} />
      </label>
    </fieldset>
  );
}
