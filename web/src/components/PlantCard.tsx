import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Plus, Sprout } from "lucide-react";
import { apiFetch } from "../api/client";
import type { Plant, PlantDetail } from "../api/types";
import { getIcon } from "../lib/icons";
import QuickLogModal from "./QuickLogModal";

function overdueBadge(daysOverdue: number | null) {
  if (daysOverdue === null) return null;
  if (daysOverdue > 0) return <span className="badge overdue">{daysOverdue}d overdue</span>;
  if (daysOverdue === 0) return <span className="badge due-today">Due today</span>;
  return <span className="badge ok">On schedule</span>;
}

export default function PlantCard({ plant }: { plant: Plant }) {
  const [quickLogOpen, setQuickLogOpen] = useState(false);
  const { data: detail } = useQuery({
    queryKey: ["plant", plant.id],
    queryFn: () => apiFetch<PlantDetail>(`/plants/${plant.id}`),
  });

  const nextReminder = detail?.reminder_states
    .filter((rs) => rs.due_date)
    .sort((a, b) => (b.days_overdue ?? -Infinity) - (a.days_overdue ?? -Infinity))[0];

  const ReminderIcon = getIcon(nextReminder?.reminder_type_icon);

  return (
    <Link to={`/plants/${plant.id}`} className="card" style={{ textDecoration: "none", color: "inherit", display: "block" }}>
      <div style={{ display: "flex", gap: "0.85rem", alignItems: "center", marginBottom: "0.6rem" }}>
        {plant.avatar_photo_path ? (
          <img
            src={`/photos/${plant.avatar_photo_path}`}
            alt=""
            style={{ width: 48, height: 48, borderRadius: "999px", objectFit: "cover", flexShrink: 0 }}
          />
        ) : (
          <span
            style={{
              width: 48,
              height: 48,
              borderRadius: "999px",
              background: "var(--accent-soft)",
              color: "var(--accent)",
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <Sprout size={22} />
          </span>
        )}
        <div style={{ minWidth: 0 }}>
          <h3 style={{ margin: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{plant.name}</h3>
          {plant.species && (
            <p style={{ color: "var(--text-muted)", margin: 0, fontSize: "0.85rem", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {plant.species}
            </p>
          )}
        </div>
      </div>
      {detail?.active_phase_windows.map((w) => (
        <span key={w.id} className="badge neutral" style={{ marginRight: "0.35rem" }}>
          {w.phase_type_name}
        </span>
      ))}
      {nextReminder && (
        <div style={{ marginTop: "0.6rem", display: "flex", alignItems: "center", gap: "0.6rem" }}>
          <button
            type="button"
            className="btn icon-btn secondary icon-btn-done"
            style={{ position: "relative" }}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setQuickLogOpen(true);
            }}
            aria-label={`Log ${nextReminder.reminder_type_name}`}
            title={`Log ${nextReminder.reminder_type_name}`}
          >
            {ReminderIcon ? <ReminderIcon size={15} /> : <Sprout size={15} />}
            <span
              style={{
                position: "absolute",
                bottom: -3,
                right: -3,
                width: "0.95rem",
                height: "0.95rem",
                borderRadius: "999px",
                background: "var(--ok)",
                color: "var(--bg)",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Plus size={10} strokeWidth={3} />
            </span>
          </button>
          <strong>{nextReminder.reminder_type_name}</strong>
          {overdueBadge(nextReminder.days_overdue)}
        </div>
      )}
      {nextReminder && (
        <QuickLogModal
          open={quickLogOpen}
          plantId={plant.id}
          reminderTypeId={nextReminder.reminder_type_id}
          reminderTypeName={nextReminder.reminder_type_name ?? "reminder"}
          onClose={() => setQuickLogOpen(false)}
        />
      )}
    </Link>
  );
}
