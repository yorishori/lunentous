export interface CalendarMarker {
  plantName: string;
  reminderTypeName: string;
  projected: boolean;
}

interface Props {
  year: number;
  month: number; // 1-12
  markersByDate: Map<string, CalendarMarker[]>;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

export default function CalendarGrid({ year, month, markersByDate }: Props) {
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
        if (day === null) return <div key={i} style={{ background: "var(--bg)", minHeight: "6rem" }} />;
        const iso = `${year}-${pad(month)}-${pad(day)}`;
        const markers = markersByDate.get(iso) ?? [];
        const isToday = iso === todayIso;
        return (
          <div
            key={i}
            style={{
              background: "var(--surface)",
              minHeight: "6rem",
              padding: "0.35rem",
              outline: isToday ? "2px solid var(--accent)" : "none",
              outlineOffset: "-2px",
            }}
          >
            <div style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>{day}</div>
            {markers.map((m, idx) => (
              <div
                key={idx}
                title={`${m.plantName} — ${m.reminderTypeName}`}
                style={{
                  fontSize: "0.7rem",
                  marginTop: "0.15rem",
                  padding: "0.1rem 0.3rem",
                  borderRadius: "4px",
                  background: m.projected ? "transparent" : "var(--accent-soft)",
                  border: m.projected ? "1px dashed var(--text-muted)" : "none",
                  color: m.projected ? "var(--text-muted)" : "var(--accent)",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {m.plantName}: {m.reminderTypeName}
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
}
