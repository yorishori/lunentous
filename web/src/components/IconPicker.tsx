import { useEffect, useMemo, useRef, useState } from "react";
import { ICON_NAMES, getIcon } from "../lib/icons";

interface Props {
  value: string | null;
  onChange: (icon: string) => void;
}

export default function IconPicker({ value, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  const filtered = useMemo(
    () => ICON_NAMES.filter((n) => n.toLowerCase().includes(search.toLowerCase())),
    [search]
  );

  const SelectedIcon = getIcon(value);

  return (
    <div className="icon-picker" ref={rootRef}>
      <button type="button" className="icon-picker-trigger" onClick={() => setOpen((o) => !o)}>
        {SelectedIcon ? <SelectedIcon size={18} /> : <span style={{ color: "var(--text-muted)" }}>—</span>}
        <span>{value ?? "Choose an icon"}</span>
      </button>
      {open && (
        <div className="icon-picker-dropdown">
          <input
            className="icon-picker-search"
            placeholder="Search icons…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            autoFocus
          />
          <div className="icon-picker-grid">
            {filtered.map((name) => {
              const Comp = getIcon(name);
              if (!Comp) return null;
              return (
                <button
                  key={name}
                  type="button"
                  className={`icon-option${value === name ? " selected" : ""}`}
                  title={name}
                  onClick={() => {
                    onChange(name);
                    setOpen(false);
                  }}
                >
                  <Comp size={18} />
                </button>
              );
            })}
            {filtered.length === 0 && (
              <p style={{ gridColumn: "1 / -1", color: "var(--text-muted)", fontSize: "0.85rem", margin: 0 }}>
                No icons found
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
