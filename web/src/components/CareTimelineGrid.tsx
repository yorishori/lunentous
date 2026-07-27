import type { RefObject } from "react";
import { CalendarRange } from "lucide-react";
import { getIcon } from "../lib/icons";
import type { Plant } from "../api/types";
import type { CareActivity, CareEvent, SeasonRange, WeekInfo } from "../lib/careTimeline";

const WEEK_WIDTH_REM = 2.3;
const LABEL_WIDTH = "7.5rem";
const RANGE_STRIP_REM = 1.15;
const POINT_STRIP_REM = 1.05;
const ROW_GAP_REM = 0.4;
const PLANT_ROW_HEIGHT_REM = RANGE_STRIP_REM + POINT_STRIP_REM + 0.3;
const MONTH_HEADER_HEIGHT = "1.7rem";
const WEEK_TICK_HEIGHT = "1.4rem";

interface Props {
  weeks: WeekInfo[];
  activities: CareActivity[];
  plants: Plant[];
  ranges: SeasonRange[];
  events: CareEvent[];
  selectedWeek: number;
  onSelectWeek: (index: number) => void;
  scrollContainerRef: RefObject<HTMLDivElement>;
}

/**
 * Rows are plants, columns are weeks (grouped by month) -- mirrors the
 * Android app's CareTimelineScreen.kt. Each plant's row combines its own
 * phase-window range pills (top strip, up to 2 concurrent types stacked)
 * and reminder point dots (bottom strip); the legend above decodes which
 * color/icon belongs to which activity type, since the row label is now
 * the plant rather than the activity.
 *
 * The label column and the scrollable grid share the exact same
 * `gap`/row sequence (header spacer, one row per plant, tick spacer) so
 * they stay aligned without any extra sync code.
 */
export default function CareTimelineGrid({ weeks, activities, plants, ranges, events, selectedWeek, onSelectWeek, scrollContainerRef }: Props) {
  const todayIso = new Date().toISOString().slice(0, 10);
  const rangesByPlant = groupBy(ranges, (r) => r.plantId);
  const eventsByPlant = groupBy(events, (e) => e.plantId);
  const activitiesById = new Map(activities.map((a) => [a.id, a]));

  return (
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.85rem", marginBottom: "0.85rem" }}>
        {activities.map((activity) => (
          <div key={activity.id} style={{ display: "flex", alignItems: "center", gap: "0.35rem" }}>
            <span style={{ display: "inline-block", width: "0.5rem", height: "0.5rem", borderRadius: "999px", background: activity.color }} />
            <span style={{ fontSize: "0.8rem" }}>{activity.label}</span>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", padding: "0.4rem 0" }}>
        <div style={{ width: LABEL_WIDTH, flexShrink: 0, display: "flex", flexDirection: "column", gap: `${ROW_GAP_REM}rem` }}>
          <div style={{ height: MONTH_HEADER_HEIGHT }} />
          {plants.map((plant) => (
            <div
              key={plant.id}
              style={{
                height: `${PLANT_ROW_HEIGHT_REM}rem`,
                display: "flex",
                alignItems: "center",
                fontSize: "0.78rem",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                paddingRight: "0.5rem",
              }}
            >
              {plant.name}
            </div>
          ))}
          <div style={{ height: WEEK_TICK_HEIGHT }} />
        </div>

        <div ref={scrollContainerRef} style={{ overflowX: "auto", flex: 1 }}>
          <div style={{ width: `${weeks.length * WEEK_WIDTH_REM}rem`, display: "flex", flexDirection: "column", gap: `${ROW_GAP_REM}rem`, position: "relative" }}>
            {/* One continuous rounded highlight for the whole selected
                column, instead of each row drawing its own separate box --
                see .care-timeline-selection in index.css. */}
            {weeks[selectedWeek] && (
              <div
                className="care-timeline-selection"
                style={{ left: `${selectedWeek * WEEK_WIDTH_REM}rem`, width: `${WEEK_WIDTH_REM}rem` }}
              />
            )}

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

            {/* Plant rows */}
            {plants.map((plant) => {
              const plantRanges = rangesByPlant.get(plant.id) ?? [];
              const plantEvents = eventsByPlant.get(plant.id) ?? [];
              const rangeActivityIds = [...new Set(plantRanges.map((r) => r.activityId))];

              return (
                <div key={plant.id} style={{ display: "flex", height: `${PLANT_ROW_HEIGHT_REM}rem` }}>
                  {weeks.map((week) => {
                    const activeRangeIds = rangeActivityIds.filter((id) =>
                      plantRanges.some((r) => r.activityId === id && week.index >= r.startWeek && week.index <= r.endWeek)
                    );
                    const activeEventIds = [...new Set(plantEvents.filter((e) => e.week === week.index).map((e) => e.activityId))];
                    const shown = activeRangeIds.slice(0, 2);
                    const subHeightRem = shown.length > 1 ? RANGE_STRIP_REM / 2 : RANGE_STRIP_REM;

                    return (
                      <WeekCell
                        key={week.index}
                        week={week}
                        selected={week.index === selectedWeek}
                        onSelect={onSelectWeek}
                        height={`${PLANT_ROW_HEIGHT_REM}rem`}
                      >
                        <div style={{ display: "flex", flexDirection: "column", gap: "0.15rem", width: "100%", padding: "0 0.1rem" }}>
                          <div style={{ height: `${RANGE_STRIP_REM}rem`, display: "flex", flexDirection: "column", gap: "1px" }}>
                            {shown.map((id) => {
                              const color = activitiesById.get(id)?.color ?? "#8839ef";
                              const rangesForType = plantRanges.filter((r) => r.activityId === id);
                              const roundStart = !rangesForType.some((r) => week.index - 1 >= r.startWeek && week.index - 1 <= r.endWeek);
                              const roundEnd = !rangesForType.some((r) => week.index + 1 >= r.startWeek && week.index + 1 <= r.endWeek);
                              return (
                                <div
                                  key={id}
                                  style={{
                                    height: `${subHeightRem}rem`,
                                    background: `${color}c0`,
                                    borderTopLeftRadius: roundStart ? "999px" : 0,
                                    borderBottomLeftRadius: roundStart ? "999px" : 0,
                                    borderTopRightRadius: roundEnd ? "999px" : 0,
                                    borderBottomRightRadius: roundEnd ? "999px" : 0,
                                  }}
                                />
                              );
                            })}
                          </div>
                          <div style={{ height: `${POINT_STRIP_REM}rem`, display: "flex", alignItems: "center", justifyContent: "center", gap: "0.2rem" }}>
                            {activeEventIds.slice(0, 3).map((id) => (
                              <span
                                key={id}
                                style={{ width: "0.5rem", height: "0.5rem", borderRadius: "999px", background: activitiesById.get(id)?.color ?? "#8839ef" }}
                              />
                            ))}
                          </div>
                        </div>
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

function groupBy<T, K>(items: T[], key: (item: T) => K): Map<K, T[]> {
  const map = new Map<K, T[]>();
  for (const item of items) {
    const k = key(item);
    const list = map.get(k);
    if (list) list.push(item);
    else map.set(k, [item]);
  }
  return map;
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
      className="care-timeline-cell"
      style={{ width: `${WEEK_WIDTH_REM}rem`, height, flexShrink: 0 }}
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
