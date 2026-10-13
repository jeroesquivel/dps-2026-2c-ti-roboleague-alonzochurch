export default function Field(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label>
      {props.label}
      <input type={props.type ?? 'text'} value={props.value} onChange={(event) => props.onChange(event.target.value)} />
    </label>
  );
}
