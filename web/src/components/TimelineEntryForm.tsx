import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type { ReminderType } from "../api/types";

interface Props {
  plantId: number;
  reminderTypes: ReminderType[];
}

export default function TimelineEntryForm({ plantId, reminderTypes }: Props) {
  const queryClient = useQueryClient();
  const [eventDate, setEventDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [reminderTypeId, setReminderTypeId] = useState<string>("");
  const [text, setText] = useState("");
  const [files, setFiles] = useState<FileList | null>(null);

  const create = useMutation({
    mutationFn: async () => {
      const formData = new FormData();
      formData.append("event_date", eventDate);
      if (reminderTypeId) formData.append("reminder_type_id", reminderTypeId);
      if (text) formData.append("text", text);
      if (files) {
        for (const file of Array.from(files)) formData.append("photo", file);
      }
      return apiFetch(`/plants/${plantId}/timeline`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["timeline", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      setText("");
      setFiles(null);
      setReminderTypeId("");
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    create.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="card" style={{ marginBottom: "1rem" }}>
      <div className="form-row">
        <label>Date</label>
        <input type="date" value={eventDate} onChange={(e) => setEventDate(e.target.value)} required />
      </div>
      <div className="form-row">
        <label>Reminder type (optional -- tags this entry as completing a reminder)</label>
        <select value={reminderTypeId} onChange={(e) => setReminderTypeId(e.target.value)}>
          <option value="">Journal note only</option>
          {reminderTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label>Notes</label>
        <textarea value={text} onChange={(e) => setText(e.target.value)} rows={2} />
      </div>
      <div className="form-row">
        <label>Photos</label>
        <input type="file" accept="image/*" multiple onChange={(e) => setFiles(e.target.files)} />
      </div>
      <button type="submit" className="btn" disabled={create.isPending}>
        Add entry
      </button>
      {create.isError && <p style={{ color: "var(--overdue)" }}>{(create.error as ApiError).message}</p>}
    </form>
  );
}
