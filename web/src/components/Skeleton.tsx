import type { CSSProperties } from "react";

interface Props {
  width?: string | number;
  height?: string | number;
  style?: CSSProperties;
}

export default function Skeleton({ width = "100%", height = "1rem", style }: Props) {
  return <div className="skeleton" style={{ width, height, ...style }} />;
}
