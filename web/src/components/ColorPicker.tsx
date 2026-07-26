import { useEffect, useRef, useState } from "react";

// Catppuccin Mocha's accent colors -- the same family the app's own theme
// (index.css) is built from, so type/phase colors always feel native.
const PALETTE = [
  "#f5e0dc",
  "#f2cdcd",
  "#f5c2e7",
  "#cba6f7",
  "#f38ba8",
  "#eba0ac",
  "#fab387",
  "#f9e2af",
  "#a6e3a1",
  "#94e2d5",
  "#89dceb",
  "#74c7ec",
  "#89b4fa",
  "#b4befe",
];

interface Props {
  value: string;
  onChange: (color: string) => void;
}

export default function ColorPicker({ value, onChange }: Props) {
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

  return (
    <div className="icon-picker" ref={rootRef}>
      <button type="button" className="color-picker-trigger" onClick={() => setOpen((o) => !o)}>
        <span className="color-swatch" style={{ background: value }} />
        <span style={{ fontSize: "0.85rem" }}>{value}</span>
      </button>
      {open && (
        <div className="color-picker-dropdown">
          <div className="color-picker-grid">
            {PALETTE.map((c) => (
              <button
                key={c}
                type="button"
                className={`color-swatch-btn${value.toLowerCase() === c ? " selected" : ""}`}
                style={{ background: c }}
                title={c}
                onClick={() => {
                  onChange(c);
                  setOpen(false);
                }}
              />
            ))}
          </div>
          <label className="color-picker-custom">
            Custom
            <input type="color" value={value} onChange={(e) => onChange(e.target.value)} />
          </label>
        </div>
      )}
    </div>
  );
}
