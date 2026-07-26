import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export default function SlideOver({ open, title, onClose, children }: Props) {
  useEffect(() => {
    if (!open) return;
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleKey);
      document.body.style.overflow = "";
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <>
      <div className="slideover-backdrop" onClick={onClose} />
      <div className="slideover-panel" role="dialog" aria-modal="true" aria-label={title}>
        <div className="slideover-header">
          <h2>{title}</h2>
          <button type="button" className="btn icon-btn secondary" onClick={onClose} aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="slideover-body">{children}</div>
      </div>
    </>,
    document.body
  );
}
