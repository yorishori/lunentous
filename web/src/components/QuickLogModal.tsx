import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import { useToast } from "./Toast";
import { hasDuplicateEntry } from "../lib/duplicateCheck";
import Modal from "./Modal";
import Spinner from "./Spinner";

interface Props {
  open: boolean;
  plantId: number;
  reminderTypeId: number;
  reminderTypeName: string;
  onClose: () => void;
}

export default function QuickLogModal({ open, plantId, reminderTypeId, reminderTypeName, onClose }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showCustom, setShowCustom] = useState(false);
  const [customDate, setCustomDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [confirmDate, setConfirmDate] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);

  const log = useMutation({
    mutationFn: (eventDate: string) => {
      const formData = new FormData();
      formData.append("event_date", eventDate);
      formData.append("reminder_type_id", String(reminderTypeId));
      return apiFetch(`/plants/${plantId}/timeline`, { method: "POST", body: formData, isFormData: true });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["reminder-states"] });
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["timeline", plantId] });
      showToast(`${reminderTypeName} logged`, "success");
      handleClose();
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to log entry", "error"),
  });

  async function attemptLog(date: string) {
    setChecking(true);
    const duplicate = await hasDuplicateEntry(plantId, reminderTypeId, date).catch(() => false);
    setChecking(false);
    if (duplicate) {
      setConfirmDate(date);
      return;
    }
    log.mutate(date);
  }

  function handleClose() {
    setShowCustom(false);
    setConfirmDate(null);
    onClose();
  }

  const busy = checking || log.isPending;

  if (confirmDate) {
    return (
      <Modal open={open} title="Already logged?" onClose={handleClose}>
        <p style={{ color: "var(--text-muted)", margin: 0 }}>
          This plant already has a "{reminderTypeName}" entry on {confirmDate}. Log it again anyway?
        </p>
        <div className="modal-actions">
          <button type="button" className="btn secondary" onClick={() => setConfirmDate(null)} disabled={log.isPending}>
            Back
          </button>
          <button type="button" className="btn" onClick={() => log.mutate(confirmDate)} disabled={log.isPending}>
            {log.isPending && <Spinner size={14} />} Log anyway
          </button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open={open} title={`Log ${reminderTypeName}`} onClose={handleClose}>
      {!showCustom ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.6rem" }}>
          <button
            type="button"
            className="btn"
            onClick={() => attemptLog(new Date().toISOString().slice(0, 10))}
            disabled={busy}
          >
            {busy && <Spinner size={14} />} Today
          </button>
          <button type="button" className="btn secondary" onClick={() => setShowCustom(true)} disabled={busy}>
            Choose a date…
          </button>
        </div>
      ) : (
        <div>
          <div className="form-row">
            <label>Date</label>
            <input type="date" value={customDate} onChange={(e) => setCustomDate(e.target.value)} autoFocus />
          </div>
          <div className="modal-actions">
            <button type="button" className="btn secondary" onClick={() => setShowCustom(false)} disabled={busy}>
              Back
            </button>
            <button type="button" className="btn" onClick={() => attemptLog(customDate)} disabled={busy}>
              {busy && <Spinner size={14} />} Log it
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}
