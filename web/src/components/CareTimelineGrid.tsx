import { CalendarRange } from "lucide-react";
import { getIcon } from "../lib/icons";
import type { CareActivity, CareEvent, SeasonRange, WeekInfo } from "../lib/careTimeline";

const WEEK_WIDTH_REM = 1.9;
const WEEK_WIDTH = `${WEEK_WIDTH_REM}rem`;
const LABEL_WIDTH = "6.5rem";
const LANE_HEIGHT = "2.1rem";
const MONTH_HEADER_HEIGHT = "1.4rem";
const WEEK_TICK_HEIGHT = "1.2rem";

interface Props {
  weeks: WeekInfo[];
  activities: CareActivity[];
  ranges: SeasonRange[];
  events: CareEvent[];
  selectedWeek: number;
  onSelectWeek: (index: number) => void;
}

/** Legend + sticky label column + one shared horizontally-scrolled grid
 * (month header, one lane per activity, a week-tick row) -- mirrors the
 * Android app's CareTimelineScreen.kt. Every row in the scrollable half
 * shares one ref'd scroll container rather than independent scroll state,
 * so they stay in lockstep with no extra sync code needed. */
export default function CareTimelineGrid({ weeks, activities, ranges, events, selectedWeek, onSelectWeek }: Props) {
  const todayIso = new Date().toISOString().slice(0, 10);

  return (
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.85rem", marginBottom: "0.75rem" }}>
        {activities.map((activity) => (
          <div key={activity.id} style={{ display: "flex", alignItems: "center", gap: "0.35rem" }}>
            <span style={{ display: "inline-block", width: "0.5rem", height: "0.5rem", borderRadius: "999px", background: activity.color }} />
            <span style={{ fontSize: "0.8rem" }}>{activity.label}</span>
          </div>
        ))}
      </div>

      <div style={{ display: "flex" }}>
        <div style={{ width: LABEL_WIDTH, flexShrink: 0 }}>
          <div style={{ height: MONTH_HEADER_HEIGHT }} />
          {activities.map((activity) => (
            <div
              key={activity.id}
              style={{
                height: LANE_HEIGHT,
                display: "flex",
                alignItems: "center",
                fontSize: "0.78rem",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                paddingRight: "0.5rem",
              }}
            >
              {activity.label}
            </div>
          ))}
          <div style={{ height: WEEK_TICK_HEIGHT }} />
        </div>

        <div style={{ overflowX: "auto", flex: 1 }}>
          <div style={{ width: `${weeks.length * WEEK_WIDTH_REM}rem` }}>
            {/* Month header row */}
            <div style={{ display: "flex", height: MONTH_HEADER_HEIGHT }}>
              {weeks.map((week) => (
                <WeekCell key={week.index} week={week} selected={week.index === selectedWeek} onSelect={onSelectWeek} height={MONTH_HEADER_HEIGHT}>
                  {week.monthLabel && (
                    <span style={{ fontSize: "0.78rem", fontWeight: 600, whiteSpace: "nowrap" }}>{week.monthLabel}</span>
                  )}
                </WeekCell>
              ))}
            </div>

            {/* Activity lanes */}
            {activities.map((activity) => {
              const activeWeeks =
                activity.kind === "range"
                  ? new Set(ranges.filter((r) => r.activityId === activity.id).flatMap((r) => rangeSpan(r)))
                  : null;
              const eventWeeks = activity.kind === "point" ? new Set(events.filter((e) => e.activityId === activity.id).map((e) => e.week)) : null;

              return (
                <div key={activity.id} style={{ display: "flex", height: LANE_HEIGHT }}>
                  {weeks.map((week) => {
                    const active = activity.kind === "range" ? activeWeeks!.has(week.index) : eventWeeks!.has(week.index);
                    return (
                      <WeekCell key={week.index} week={week} selected={week.index === selectedWeek} onSelect={onSelectWeek} height={LANE_HEIGHT}>
                        {active &&
                          (activity.kind === "range" ? (
                            <div
                              style={{
                                position: "absolute",
                                inset: "0.5rem 0",
                                background: `${activity.color}c0`,
                                borderTopLeftRadius: activeWeeks!.has(week.index - 1) ? 0 : "999px",
                                borderBottomLeftRadius: activeWeeks!.has(week.index - 1) ? 0 : "999px",
                                borderTopRightRadius: activeWeeks!.has(week.index + 1) ? 0 : "999px",
                                borderBottomRightRadius: activeWeeks!.has(week.index + 1) ? 0 : "999px",
                              }}
                            />
                          ) : (
                            <span style={{ width: "0.6rem", height: "0.6rem", borderRadius: "999px", background: activity.color }} />
                          ))}
                      </WeekCell>
                    );
                  })}
                </div>
              );
            })}

            {/* Week-tick row -- a thin accent tick for today, otherwise every 4th week's number */}
            <div style={{ display: "flex", height: WEEK_TICK_HEIGHT }}>
              {weeks.map((week) => {
                const isToday = todayIso >= week.startDate && todayIso < addDays(week.startDate, 7);
                return (
                  <WeekCell key={week.index} week={week} selected={week.index === selectedWeek} onSelect={onSelectWeek} height={WEEK_TICK_HEIGHT}>
                    {isToday ? (
                      <div style={{ width: "2px", height: WEEK_TICK_HEIGHT, background: "var(--accent)" }} />
                    ) : week.index % 4 === 0 ? (
                      <span style={{ fontSize: "0.7rem", color: "var(--text-muted)" }}>{week.index + 1}</span>
                    ) : null}
                  </WeekCell>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function rangeSpan(range: SeasonRange): number[] {
  const weeks: number[] = [];
  for (let w = range.startWeek; w <= range.endWeek; w++) weeks.push(w);
  return weeks;
}

function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
}

function WeekCell({
  week,
  selected,
  onSelect,
  height,
  children,
}: {
  week: WeekInfo;
  selected: boolean;
  onSelect: (index: number) => void;
  height: string;
  children?: React.ReactNode;
}) {
  return (
    <button
      type="button"
      className={`care-timeline-cell${selected ? " selected" : ""}`}
      style={{ width: WEEK_WIDTH, height, flexShrink: 0 }}
      onClick={() => onSelect(week.index)}
      aria-selected={selected}
      aria-label={`Week of ${week.startDate}`}
    >
      {children}
    </button>
  );
}

export function activityIcon(activity: CareActivity) {
  if (activity.kind === "range") return CalendarRange;
  return getIcon(activity.icon) ?? CalendarRange;
}
