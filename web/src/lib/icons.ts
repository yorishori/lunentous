import * as Icons from "lucide-react";
import type { LucideIcon } from "lucide-react";

// A curated subset of lucide-react icons relevant to plant care reminders
// and phases, used by the icon picker (spec-driven UX request, not in
// spec_v1.md itself). Names must match lucide-react's exported component
// names exactly (PascalCase) since they're looked up dynamically.
export const ICON_NAMES = [
  "Droplet",
  "Droplets",
  "Leaf",
  "Sprout",
  "Flower",
  "Flower2",
  "Sun",
  "Sunrise",
  "Scissors",
  "Shovel",
  "Bug",
  "SprayCan",
  "Thermometer",
  "Wind",
  "CloudRain",
  "Snowflake",
  "Moon",
  "Recycle",
  "Package",
  "Bell",
  "Heart",
  "Star",
  "Sparkles",
  "TreeDeciduous",
  "TreePine",
  "Bird",
  "Worm",
  "Beaker",
  "Pipette",
  "Fan",
  "Lightbulb",
] as const;

export function getIcon(name?: string | null): LucideIcon | null {
  if (!name) return null;
  return (Icons as unknown as Record<string, LucideIcon>)[name] ?? null;
}
