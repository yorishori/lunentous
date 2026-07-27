import type { CSSProperties } from "react";

export interface CalendarMarker {
  key: string;
  label: string;
  kind: "due" | "projected" | "logged";
  color?: string;
}

export interface PhaseBand {
  color: string | null;
  label: string;
}

interface Props {
  year: number;
  month: number; // 1-12
  markersByDate: Map<string, CalendarMarker[]>;
  phaseBandsByDate: Map<string, PhaseBand[]>;
  selectedDay: string | null;
  onDayClick: (dateIso: string) => void;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function markerStyle(marker: CalendarMarker): CSSProperties {
  if (marker.kind === "projected") {
    return {
      background: "transparent",
      border: `1px dashed ${marker.color ?? "var(--text-muted)"}`,
      color: marker.color ?? "var(--text-muted)",
    };
  }
  if (marker.kind === "logged") {
    return {
      background: marker.color ? `${marker.color}40` : "var(--ok-soft)",
      border: `1px solid ${marker.color ?? "var(--ok)"}`,
      color: marker.color ?? "var(--ok)",
    };
  }
  return {
    background: marker.color ? `${marker.color}29` : "var(--accent-soft)",
    border: "1px solid transparent",
    color: marker.color ?? "var(--accent)",
  };
}

export default function CalendarGrid({ year, month, markersByDate, phaseBandsByDate, selectedDay, onDayClick }: Props) {
  const firstDay = new Date(year, month - 1, 1);
  const daysInMonth = new Date(year, month, 0).getDate();
  const startWeekday = firstDay.getDay();

  const cells: (number | null)[] = [];
  for (let i = 0; i < startWeekday; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  while (cells.length % 7 !== 0) cells.push(null);

  const todayIso = new Date().toISOString().slice(0, 10);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(7, minmax(7rem, 1fr))", gap: "1px", background: "var(--border)" }}>
      {WEEKDAYS.map((w) => (
        <div key={w} style={{ background: "var(--surface)", padding: "0.4rem", fontWeight: 600, fontSize: "0.85rem", textAlign: "center" }}>
          {w}
        </div>
      ))}
      {cells.map((day, i) => {
        if (day === null) return <div key={i} style={{ background: "var(--bg)", minHeight: "6.5rem" }} />;
        const iso = `${year}-${pad(month)}-${pad(day)}`;
        const markers = markersByDate.get(iso) ?? [];
        const bands = phaseBandsByDate.get(iso) ?? [];
        const isToday = iso === todayIso;
        const isSelected = iso === selectedDay;
        return (
          <div
            key={i}
            className={`calendar-day-cell${isSelected ? " selected" : ""}`}
            onClick={() => onDayClick(iso)}
            title={bands.length > 0 ? bands.map((b) => b.label).join(", ") : undefined}
            style={{
              background: "var(--surface)",
              minHeight: "6.5rem",
              padding: "0.35rem",
              outline: isToday ? "2px solid var(--accent)" : "none",
              outlineOffset: "-2px",
              paddingBottom: bands.length > 0 ? "0.6rem" : "0.35rem",
            }}
          >
            <div style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>{day}</div>
            {markers.map((m) => (
              <div
                key={m.key}
                title={m.label}
                style={{
                  fontSize: "0.7rem",
                  marginTop: "0.15rem",
                  padding: "0.1rem 0.3rem",
                  borderRadius: "4px",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  ...markerStyle(m),
                }}
              >
                {m.label}
              </div>
            ))}
            {bands.slice(0, 1).map((b, idx) => (
              <div key={idx} className="calendar-phase-band" style={{ background: b.color ?? "var(--accent)" }} />
            ))}
          </div>
        );
      })}
    </div>
  );
}
