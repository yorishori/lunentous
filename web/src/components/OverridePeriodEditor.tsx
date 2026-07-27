import type { OverridePeriod } from "../api/types";
import MonthDayPicker from "./MonthDayPicker";

interface Props {
  periods: OverridePeriod[];
  onChange: (periods: OverridePeriod[]) => void;
}

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
          <MonthDayPicker
            month={p.start_month}
            day={p.start_day}
            onChange={(start_month, start_day) => update(i, { start_month, start_day })}
          />
          <span>to</span>
          <MonthDayPicker
            month={p.end_month}
            day={p.end_day}
            onChange={(end_month, end_day) => update(i, { end_month, end_day })}
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
