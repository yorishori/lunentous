import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type { OverridePeriod, ReminderRule, ReminderType } from "../api/types";
import OverridePeriodEditor from "./OverridePeriodEditor";

interface Props {
  plantId: number;
  reminderTypes: ReminderType[];
  existingRule?: ReminderRule;
  onDone?: () => void;
}

export default function ReminderRuleForm({ plantId, reminderTypes, existingRule, onDone }: Props) {
  const queryClient = useQueryClient();
  const [reminderTypeId, setReminderTypeId] = useState<number>(
    existingRule?.reminder_type_id ?? reminderTypes[0]?.id ?? 0
  );
  const [defaultInterval, setDefaultInterval] = useState<string>(
    existingRule?.default_interval_days != null ? String(existingRule.default_interval_days) : ""
  );
  const [periods, setPeriods] = useState<OverridePeriod[]>(existingRule?.override_periods ?? []);

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["reminder-rules", plantId] });
    queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
  }

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        reminder_type_id: reminderTypeId,
        default_interval_days: defaultInterval ? Number(defaultInterval) : null,
        override_periods: periods.map(({ id: _id, ...rest }) => rest),
      };
      return existingRule
        ? apiFetch(`/reminder-rules/${existingRule.id}`, { method: "PATCH", body })
        : apiFetch(`/plants/${plantId}/reminder-rules`, { method: "POST", body });
    },
    onSuccess: () => {
      invalidate();
      onDone?.();
    },
  });

  const remove = useMutation({
    mutationFn: () => apiFetch(`/reminder-rules/${existingRule!.id}`, { method: "DELETE" }),
    onSuccess: invalidate,
  });

  return (
    <div className="card" style={{ marginBottom: "0.75rem" }}>
      <div className="form-row">
        <label>Reminder type</label>
        <select value={reminderTypeId} onChange={(e) => setReminderTypeId(Number(e.target.value))} disabled={!!existingRule}>
          {reminderTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label>Default interval, in days (blank = paused outside override periods)</label>
        <input type="number" min={1} value={defaultInterval} onChange={(e) => setDefaultInterval(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Seasonal overrides</label>
        <OverridePeriodEditor periods={periods} onChange={setPeriods} />
      </div>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button type="button" className="btn" onClick={() => save.mutate()} disabled={save.isPending}>
          {existingRule ? "Save changes" : "Add rule"}
        </button>
        {existingRule && (
          <button type="button" className="btn secondary" onClick={() => remove.mutate()} disabled={remove.isPending}>
            Delete rule
          </button>
        )}
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
    </div>
  );
}
