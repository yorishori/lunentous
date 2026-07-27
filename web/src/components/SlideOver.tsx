import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { useMountTransition } from "../lib/useMountTransition";

const EXIT_DURATION = 220;

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export default function SlideOver({ open, title, onClose, children }: Props) {
  const { shouldRender, closing } = useMountTransition(open, EXIT_DURATION);

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

  if (!shouldRender) return null;

  return createPortal(
    <>
      <div className={`slideover-backdrop${closing ? " closing" : ""}`} onClick={onClose} />
      <div
        className={`slideover-panel${closing ? " closing" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
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
