import { useEffect, useState } from "react";

/** Keeps a component mounted for `duration` ms after `open` goes false, so a
 * CSS exit animation (driven by the returned `closing` flag) can play before
 * unmount -- otherwise React removes the DOM node instantly and no closing
 * transition is ever visible. */
export function useMountTransition(open: boolean, duration: number): { shouldRender: boolean; closing: boolean } {
  const [shouldRender, setShouldRender] = useState(open);
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (open) {
      setShouldRender(true);
      setClosing(false);
      return;
    }
    if (shouldRender) {
      setClosing(true);
      const timer = setTimeout(() => {
        setShouldRender(false);
        setClosing(false);
      }, duration);
      return () => clearTimeout(timer);
    }
    // Intentionally omits `shouldRender` -- it's only read here, and including
    // it would re-run this effect every time it flips, which is harmless but
    // pointless (the `open` branch is idempotent).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, duration]);

  return { shouldRender, closing };
}
