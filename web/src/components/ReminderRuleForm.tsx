import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, ChevronLeft, ChevronRight } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import type { OverridePeriod, ReminderRule, ReminderType } from "../api/types";
import { useToast } from "./Toast";
import OverridePeriodEditor from "./OverridePeriodEditor";
import Spinner from "./Spinner";
import { getIcon } from "../lib/icons";

interface Props {
  plantId: number;
  reminderTypes: ReminderType[];
  existingRule?: ReminderRule;
  onDone: () => void;
}

const STEPS = ["Type & interval", "Seasonal overrides", "Review"];

export default function ReminderRuleForm({ plantId, reminderTypes, existingRule, onDone }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [step, setStep] = useState(0);
  const [reminderTypeId, setReminderTypeId] = useState<number>(
    existingRule?.reminder_type_id ?? reminderTypes[0]?.id ?? 0
  );
  const [defaultInterval, setDefaultInterval] = useState<string>(
    existingRule?.default_interval_days != null ? String(existingRule.default_interval_days) : "4"
  );
  const [periods, setPeriods] = useState<OverridePeriod[]>(existingRule?.override_periods ?? []);

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["reminder-rules", plantId] });
    queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
    queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
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
      showToast(existingRule ? "Reminder rule updated" : "Reminder rule created", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to save reminder rule", "error"),
  });

  const remove = useMutation({
    mutationFn: () => apiFetch(`/reminder-rules/${existingRule!.id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      showToast("Reminder rule deleted", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to delete reminder rule", "error"),
  });

  const selectedType = reminderTypes.find((t) => t.id === reminderTypeId);
  const SelectedIcon = getIcon(selectedType?.icon);

  const next = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));
  const back = () => setStep((s) => Math.max(s - 1, 0));

  return (
    <div>
      <div className="wizard-steps">
        {STEPS.map((label, i) => (
          <div
            key={label}
            style={{ display: "flex", alignItems: "center", flex: i < STEPS.length - 1 ? 1 : undefined, gap: "0.4rem" }}
          >
            <div className={`wizard-step-dot${i === step ? " active" : ""}${i < step ? " done" : ""}`} title={label}>
              {i < step ? <Check size={14} /> : i + 1}
            </div>
            {i < STEPS.length - 1 && <div className="wizard-step-line" />}
          </div>
        ))}
      </div>

      <div className="wizard-panel">
        {step === 0 && (
          <>
            <div className="form-row">
              <label>Reminder type</label>
              <select
                value={reminderTypeId}
                onChange={(e) => setReminderTypeId(Number(e.target.value))}
                disabled={!!existingRule}
              >
                {reminderTypes.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>Default interval, in days</label>
              <input
                type="number"
                min={1}
                placeholder="Leave blank to pause outside override periods"
                value={defaultInterval}
                onChange={(e) => setDefaultInterval(e.target.value)}
              />
            </div>
          </>
        )}

        {step === 1 && (
          <div className="form-row">
            <label>Seasonal overrides (optional)</label>
            <p style={{ color: "var(--text-muted)", fontSize: "0.85rem", marginTop: 0 }}>
              Override the default interval during specific date ranges — e.g. water less often over winter.
            </p>
            <OverridePeriodEditor periods={periods} onChange={setPeriods} />
          </div>
        )}

        {step === 2 && (
          <div className="card" style={{ background: "var(--bg)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "0.6rem", marginBottom: "0.75rem" }}>
              <span
                style={{
                  display: "inline-flex",
                  width: "2.2rem",
                  height: "2.2rem",
                  borderRadius: "999px",
                  background: selectedType?.color ? `${selectedType.color}29` : "var(--accent-soft)",
                  color: selectedType?.color ?? "var(--accent)",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                {SelectedIcon && <SelectedIcon size={18} />}
              </span>
              <strong>{selectedType?.name}</strong>
            </div>
            <p style={{ margin: "0.25rem 0" }}>
              {defaultInterval
                ? `Every ${defaultInterval} days by default`
                : "Paused by default (no reminder outside overrides)"}
            </p>
            {periods.length > 0 ? (
              <ul style={{ margin: "0.5rem 0 0", paddingLeft: "1.2rem" }}>
                {periods.map((p, i) => (
                  <li key={i} style={{ fontSize: "0.88rem" }}>
                    {p.start_month}/{p.start_day} – {p.end_month}/{p.end_day}:{" "}
                    {p.interval_days ? `every ${p.interval_days} days` : "paused"}
                  </li>
                ))}
              </ul>
            ) : (
              <p style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>No seasonal overrides.</p>
            )}
          </div>
        )}
      </div>

      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}

      <div style={{ display: "flex", justifyContent: "space-between", marginTop: "1.5rem" }}>
        <div>
          {existingRule && (
            <button type="button" className="btn danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
              {remove.isPending && <Spinner size={14} />} Delete rule
            </button>
          )}
        </div>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          {step > 0 && (
            <button type="button" className="btn secondary" onClick={back}>
              <ChevronLeft size={16} /> Back
            </button>
          )}
          {step < STEPS.length - 1 ? (
            <button type="button" className="btn" onClick={next}>
              Next <ChevronRight size={16} />
            </button>
          ) : (
            <button type="button" className="btn" onClick={() => save.mutate()} disabled={save.isPending}>
              {save.isPending && <Spinner size={14} />} {existingRule ? "Save changes" : "Create rule"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
