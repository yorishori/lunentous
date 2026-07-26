import Modal from "./Modal";

interface Props {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  pending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = "Confirm",
  danger,
  pending,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Modal open={open} title={title} onClose={onCancel}>
      <p style={{ color: "var(--text-muted)", margin: 0 }}>{message}</p>
      <div className="modal-actions">
        <button type="button" className="btn secondary" onClick={onCancel} disabled={pending}>
          Cancel
        </button>
        <button type="button" className={`btn${danger ? " danger" : ""}`} onClick={onConfirm} disabled={pending}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
