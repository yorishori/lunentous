import { useEffect, useRef, useState } from "react";
import { ChevronDown } from "lucide-react";

interface Option {
  id: number;
  label: string;
}

interface Props {
  options: Option[];
  selected: number[];
  onChange: (ids: number[]) => void;
  placeholder?: string;
}

export default function MultiSelect({ options, selected, onChange, placeholder = "All plants" }: Props) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id]);
  }

  const label =
    selected.length === 0
      ? placeholder
      : selected.length === 1
        ? (options.find((o) => o.id === selected[0])?.label ?? placeholder)
        : `${selected.length} selected`;

  return (
    <div className="multiselect" ref={rootRef}>
      <button type="button" className="multiselect-trigger" onClick={() => setOpen((o) => !o)}>
        <span>{label}</span>
        <ChevronDown size={16} />
      </button>
      {open && (
        <div className="multiselect-dropdown">
          {options.map((opt) => (
            <label key={opt.id} className="multiselect-option">
              <input type="checkbox" checked={selected.includes(opt.id)} onChange={() => toggle(opt.id)} />
              {opt.label}
            </label>
          ))}
          {options.length === 0 && (
            <p style={{ color: "var(--text-muted)", fontSize: "0.85rem", margin: 0 }}>No plants</p>
          )}
        </div>
      )}
    </div>
  );
}
