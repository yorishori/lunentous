import { useIsFetching, useIsMutating } from "@tanstack/react-query";

/** Slim indeterminate progress bar pinned to the viewport top, visible
 * whenever any query or mutation is in flight -- a persistent, honest signal
 * that the app is doing background work (not an artificial delay). */
export default function LoadingBar() {
  const isFetching = useIsFetching();
  const isMutating = useIsMutating();

  if (isFetching === 0 && isMutating === 0) return null;

  return (
    <div className="loading-bar-track">
      <div className="loading-bar-fill" />
    </div>
  );
}
