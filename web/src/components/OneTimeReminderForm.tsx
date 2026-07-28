import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type { OneTimeReminder } from "../api/types";
import { useToast } from "./Toast";
import Spinner from "./Spinner";

interface Props {
  plantId: number;
  existingReminder?: OneTimeReminder;
  onDone: () => void;
}

/** Untyped, no-log, per-plant reminder -- just a date + freeform text (e.g.
 * "give this plant to a friend", "buy a new pot"). Much simpler than
 * ReminderRuleForm since there's no type/interval/override periods to
 * configure. Completing one happens from the list row itself (a PATCH
 * toggling completed_at), not from this form. */
export default function OneTimeReminderForm({ plantId, existingReminder, onDone }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [dueDate, setDueDate] = useState(existingReminder?.due_date ?? new Date().toISOString().slice(0, 10));
  const [text, setText] = useState(existingReminder?.text ?? "");

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["one-time-reminders", plantId] });
    queryClient.invalidateQueries({ queryKey: ["one-time-reminders"] });
  }

  const save = useMutation({
    mutationFn: async () => {
      const body = { due_date: dueDate, text };
      return existingReminder
        ? apiFetch(`/one-time-reminders/${existingReminder.id}`, { method: "PATCH", body })
        : apiFetch(`/plants/${plantId}/one-time-reminders`, { method: "POST", body });
    },
    onSuccess: () => {
      invalidate();
      showToast(existingReminder ? "Reminder updated" : "Reminder added", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to save reminder", "error"),
  });

  const remove = useMutation({
    mutationFn: () => apiFetch(`/one-time-reminders/${existingReminder!.id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      showToast("Reminder deleted", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to delete reminder", "error"),
  });

  return (
    <div>
      <div className="form-row">
        <label>Date</label>
        <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </div>
      <div className="form-row">
        <label>What's this reminder for?</label>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={2}
          placeholder="e.g. Give this plant to a friend"
        />
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
      <div style={{ display: "flex", justifyContent: "space-between", marginTop: "1.5rem" }}>
        <div>
          {existingReminder && (
            <button type="button" className="btn danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
              {remove.isPending && <Spinner size={14} />} Delete
            </button>
          )}
        </div>
        <button type="button" className="btn" onClick={() => save.mutate()} disabled={save.isPending || !text.trim()}>
          {save.isPending && <Spinner size={14} />} {existingReminder ? "Save changes" : "Add reminder"}
        </button>
      </div>
    </div>
  );
}
