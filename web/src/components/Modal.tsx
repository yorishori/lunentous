import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useMountTransition } from "../lib/useMountTransition";

const EXIT_DURATION = 180;

interface Props {
  open: boolean;
  title?: string;
  onClose: () => void;
  children: ReactNode;
}

export default function Modal({ open, title, onClose, children }: Props) {
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
    <div className={`modal-backdrop${closing ? " closing" : ""}`} onClick={onClose}>
      <div
        className={`modal-box${closing ? " closing" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        {title && <h2>{title}</h2>}
        {children}
      </div>
    </div>,
    document.body
  );
}
