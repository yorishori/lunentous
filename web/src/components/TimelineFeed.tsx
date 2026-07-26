import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { ReminderType, TimelineEvent } from "../api/types";
import { useToast } from "./Toast";

interface Props {
  plantId: number;
  reminderTypes: ReminderType[];
}

export default function TimelineFeed({ plantId, reminderTypes }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [filterTypeId, setFilterTypeId] = useState<string>("");
  const [cursor, setCursor] = useState<number | undefined>(undefined);
  const [allEvents, setAllEvents] = useState<TimelineEvent[]>([]);

  const query = useQuery({
    queryKey: ["timeline", plantId, filterTypeId, cursor],
    queryFn: async () => {
      const params = new URLSearchParams({ limit: "20" });
      if (filterTypeId) params.set("reminder_type_id", filterTypeId);
      if (cursor) params.set("before", String(cursor));
      const events = await apiFetch<TimelineEvent[]>(`/plants/${plantId}/timeline?${params.toString()}`);
      setAllEvents((prev) => (cursor ? [...prev, ...events] : events));
      return events;
    },
  });

  function resetFilter(value: string) {
    setFilterTypeId(value);
    setCursor(undefined);
    setAllEvents([]);
  }

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/timeline/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      setCursor(undefined);
      setAllEvents([]);
      queryClient.invalidateQueries({ queryKey: ["timeline", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
      showToast("Timeline entry deleted", "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to delete timeline entry", "error"),
  });

  const typeName = (id: number | null) => reminderTypes.find((t) => t.id === id)?.name;

  return (
    <div>
      <div className="form-row" style={{ maxWidth: "16rem" }}>
        <label>Filter by reminder type</label>
        <select value={filterTypeId} onChange={(e) => resetFilter(e.target.value)}>
          <option value="">All entries</option>
          {reminderTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>

      {allEvents.length === 0 && !query.isLoading && <p>No timeline entries yet.</p>}

      {allEvents.map((event) => (
        <div key={event.id} className="card" style={{ marginBottom: "0.5rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <strong>{event.event_date}</strong>
            <button
              type="button"
              className="btn icon-btn secondary"
              onClick={() => remove.mutate(event.id)}
              aria-label="Delete entry"
            >
              <Trash2 size={15} />
            </button>
          </div>
          {event.reminder_type_id != null && <span className="badge ok">{typeName(event.reminder_type_id)}</span>}
          {event.text && <p>{event.text}</p>}
          {event.photos.length > 0 && (
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.5rem" }}>
              {event.photos.map((p) => (
                <img
                  key={p.id}
                  src={`/photos/${p.file_path}`}
                  alt=""
                  style={{ width: 80, height: 80, objectFit: "cover", borderRadius: "8px" }}
                />
              ))}
            </div>
          )}
        </div>
      ))}

      {query.data && query.data.length === 20 && (
        <button
          type="button"
          className="btn secondary"
          onClick={() => setCursor(allEvents[allEvents.length - 1]?.id)}
        >
          Load more
        </button>
      )}
    </div>
  );
}
