import type { OverridePeriod } from "../api/types";

interface Props {
  periods: OverridePeriod[];
  onChange: (periods: OverridePeriod[]) => void;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export default function OverridePeriodEditor({ periods, onChange }: Props) {
  function update(index: number, patch: Partial<OverridePeriod>) {
    onChange(periods.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  }

  function remove(index: number) {
    onChange(periods.filter((_, i) => i !== index));
  }

  function add() {
    onChange([...periods, { start_month: 1, start_day: 1, end_month: 1, end_day: 1, interval_days: null }]);
  }

  return (
    <div>
      {periods.map((p, i) => (
        <div key={i} style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginBottom: "0.5rem", flexWrap: "wrap" }}>
          <select value={p.start_month} onChange={(e) => update(i, { start_month: Number(e.target.value) })}>
            {MONTHS.map((m, idx) => (
              <option key={m} value={idx + 1}>
                {m}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            max={31}
            value={p.start_day}
            onChange={(e) => update(i, { start_day: Number(e.target.value) })}
            style={{ width: "3.5rem" }}
          />
          <span>to</span>
          <select value={p.end_month} onChange={(e) => update(i, { end_month: Number(e.target.value) })}>
            {MONTHS.map((m, idx) => (
              <option key={m} value={idx + 1}>
                {m}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            max={31}
            value={p.end_day}
            onChange={(e) => update(i, { end_day: Number(e.target.value) })}
            style={{ width: "3.5rem" }}
          />
          <span>@</span>
          <input
            type="number"
            min={1}
            placeholder="paused"
            value={p.interval_days ?? ""}
            onChange={(e) => update(i, { interval_days: e.target.value ? Number(e.target.value) : null })}
            style={{ width: "5rem" }}
          />
          <span>days</span>
          <button type="button" className="btn secondary" onClick={() => remove(i)}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" className="btn secondary" onClick={add}>
        + Add override period
      </button>
    </div>
  );
}
