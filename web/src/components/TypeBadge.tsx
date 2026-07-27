interface Props {
  name: string;
  color?: string | null;
}

/** A reminder-type tag colored to match the type itself, rather than a
 * fixed badge color -- used anywhere a timeline entry shows its tag. */
export default function TypeBadge({ name, color }: Props) {
  return (
    <span
      className="badge"
      style={{
        background: color ? `${color}29` : "var(--accent-soft)",
        color: color ?? "var(--accent)",
      }}
    >
      {name}
    </span>
  );
}
