import { useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface Props {
  month: number; // 1-12
  day: number;
  onChange: (month: number, day: number) => void;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
// Year-agnostic (these dates recur annually with no year attached), so
// February is always treated as having 29 days rather than picking a
// reference year -- lets a window's end genuinely reach Feb 29.
const DAYS_IN_MONTH = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

/**
 * Replaces a bare "month select + day number input" pair with an actual
 * small calendar popover -- used for reminder rule override periods and
 * phase windows, both of which are month/day ranges with no year attached.
 */
export default function MonthDayPicker({ month, day, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const [viewMonth, setViewMonth] = useState(month);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) setViewMonth(month);
  }, [open, month]);

  useEffect(() => {
    if (!open) return;
    function handleClick(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  function prevMonth() {
    setViewMonth((m) => (m === 1 ? 12 : m - 1));
  }

  function nextMonth() {
    setViewMonth((m) => (m === 12 ? 1 : m + 1));
  }

  function pickDay(d: number) {
    onChange(viewMonth, d);
    setOpen(false);
  }

  const daysInViewMonth = DAYS_IN_MONTH[viewMonth - 1];

  return (
    <div className="month-day-picker" ref={rootRef}>
      <button type="button" className="month-day-picker-trigger" onClick={() => setOpen((o) => !o)}>
        {MONTHS[month - 1]} {day}
      </button>
      {open && (
        <div className="month-day-picker-dropdown">
          <div className="month-day-picker-header">
            <button type="button" className="btn secondary" onClick={prevMonth} aria-label="Previous month">
              <ChevronLeft size={14} />
            </button>
            <strong>{MONTHS[viewMonth - 1]}</strong>
            <button type="button" className="btn secondary" onClick={nextMonth} aria-label="Next month">
              <ChevronRight size={14} />
            </button>
          </div>
          <div className="month-day-picker-grid">
            {Array.from({ length: daysInViewMonth }, (_, i) => i + 1).map((d) => (
              <button
                key={d}
                type="button"
                className={`month-day-picker-cell${viewMonth === month && d === day ? " selected" : ""}`}
                onClick={() => pickDay(d)}
              >
                {d}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
