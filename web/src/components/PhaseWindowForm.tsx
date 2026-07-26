import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type { PhaseType, PhaseWindow } from "../api/types";
import { useToast } from "./Toast";

interface Props {
  plantId: number;
  phaseTypes: PhaseType[];
  existingWindow?: PhaseWindow;
  onDone: () => void;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

export default function PhaseWindowForm({ plantId, phaseTypes, existingWindow, onDone }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [phaseTypeId, setPhaseTypeId] = useState<number>(existingWindow?.phase_type_id ?? phaseTypes[0]?.id ?? 0);
  const [startMonth, setStartMonth] = useState(existingWindow?.start_month ?? 1);
  const [startDay, setStartDay] = useState(existingWindow?.start_day ?? 1);
  const [endMonth, setEndMonth] = useState(existingWindow?.end_month ?? 1);
  const [endDay, setEndDay] = useState(existingWindow?.end_day ?? 1);
  const [notes, setNotes] = useState(existingWindow?.notes ?? "");

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ["phase-windows", plantId] });
    queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
  }

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        phase_type_id: phaseTypeId,
        start_month: startMonth,
        start_day: startDay,
        end_month: endMonth,
        end_day: endDay,
        notes: notes || null,
      };
      return existingWindow
        ? apiFetch(`/phase-windows/${existingWindow.id}`, { method: "PATCH", body })
        : apiFetch(`/plants/${plantId}/phase-windows`, { method: "POST", body });
    },
    onSuccess: () => {
      invalidate();
      showToast(existingWindow ? "Phase window updated" : "Phase window created", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to save phase window", "error"),
  });

  const remove = useMutation({
    mutationFn: () => apiFetch(`/phase-windows/${existingWindow!.id}`, { method: "DELETE" }),
    onSuccess: () => {
      invalidate();
      showToast("Phase window deleted", "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to delete phase window", "error"),
  });

  return (
    <div>
      <div className="form-row">
        <label>Phase type</label>
        <select value={phaseTypeId} onChange={(e) => setPhaseTypeId(Number(e.target.value))}>
          {phaseTypes.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <div className="form-row">
        <label>Window</label>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", flexWrap: "wrap" }}>
          <select value={startMonth} onChange={(e) => setStartMonth(Number(e.target.value))}>
            {MONTHS.map((m, idx) => (
              <option key={m} value={idx + 1}>
                {m}
              </option>
            ))}
          </select>
          <input type="number" min={1} max={31} value={startDay} onChange={(e) => setStartDay(Number(e.target.value))} style={{ width: "3.5rem" }} />
          <span>to</span>
          <select value={endMonth} onChange={(e) => setEndMonth(Number(e.target.value))}>
            {MONTHS.map((m, idx) => (
              <option key={m} value={idx + 1}>
                {m}
              </option>
            ))}
          </select>
          <input type="number" min={1} max={31} value={endDay} onChange={(e) => setEndDay(Number(e.target.value))} style={{ width: "3.5rem" }} />
        </div>
      </div>
      <div className="form-row">
        <label>Notes</label>
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} />
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
      <div style={{ display: "flex", justifyContent: "space-between", marginTop: "1.5rem" }}>
        <div>
          {existingWindow && (
            <button type="button" className="btn danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
              Delete
            </button>
          )}
        </div>
        <button type="button" className="btn" onClick={() => save.mutate()} disabled={save.isPending}>
          {existingWindow ? "Save changes" : "Add phase window"}
        </button>
      </div>
    </div>
  );
}
