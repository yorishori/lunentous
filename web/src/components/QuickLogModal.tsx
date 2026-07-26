import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import { useToast } from "./Toast";
import Modal from "./Modal";

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

  function handleClose() {
    setShowCustom(false);
    onClose();
  }

  return (
    <Modal open={open} title={`Log ${reminderTypeName}`} onClose={handleClose}>
      {!showCustom ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.6rem" }}>
          <button
            type="button"
            className="btn"
            onClick={() => log.mutate(new Date().toISOString().slice(0, 10))}
            disabled={log.isPending}
          >
            Today
          </button>
          <button type="button" className="btn secondary" onClick={() => setShowCustom(true)}>
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
            <button type="button" className="btn secondary" onClick={() => setShowCustom(false)}>
              Back
            </button>
            <button type="button" className="btn" onClick={() => log.mutate(customDate)} disabled={log.isPending}>
              Log it
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}
