import { useId, type ReactNode } from "react";
import { Input, Select, Textarea } from "./ui/input";
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: (id: string) => ReactNode;
  hint?: string;
}) {
  const id = useId();
  return (
    <div className="space-y-1.5">
      <label
        htmlFor={id}
        className="block text-sm font-semibold text-slate-700"
      >
        {label}
      </label>
      {children(id)}
      {hint && <p className="text-xs leading-relaxed text-slate-500">{hint}</p>}
    </div>
  );
}
export function TextField({
  label,
  name,
  value,
  required = true,
  type = "text",
  maxLength = 500,
  min,
  max,
  hint,
}: {
  label: string;
  name: string;
  value?: string | number | null;
  required?: boolean;
  type?: string;
  maxLength?: number;
  min?: string | number;
  max?: string | number;
  hint?: string;
}) {
  return (
    <Field label={label} hint={hint}>
      {(id) => (
        <Input
          id={id}
          name={name}
          defaultValue={value ?? ""}
          required={required}
          type={type}
          min={min}
          max={max}
          maxLength={maxLength}
        />
      )}
    </Field>
  );
}
export { Input, Select, Textarea };
