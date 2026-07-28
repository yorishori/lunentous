import {
  Droplet,
  Droplets,
  Leaf,
  Sprout,
  Flower,
  Flower2,
  Sun,
  Sunrise,
  Scissors,
  Shovel,
  Bug,
  SprayCan,
  Thermometer,
  Wind,
  CloudRain,
  Snowflake,
  Moon,
  Recycle,
  Package,
  Bell,
  Heart,
  Star,
  Sparkles,
  TreeDeciduous,
  TreePine,
  Bird,
  Worm,
  Beaker,
  Pipette,
  Fan,
  Lightbulb,
  Pin,
  type LucideIcon,
} from "lucide-react";

// A curated subset of lucide-react icons relevant to plant care reminders
// and phases, used by the icon picker. Names must match lucide-react's
// exported component names exactly (PascalCase) since they're looked up
// dynamically -- see ARCHITECTURE.md's IconPicker note.
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

// Explicit named imports (rather than `import * as Icons`) so Vite/Rollup
// can tree-shake the ~1000 lucide-react icons never used here -- a
// wildcard import defeats that, since every icon becomes reachable via a
// dynamic property lookup. "Pin" isn't in ICON_NAMES (it's not a
// user-pickable reminder/phase type icon) but is looked up by name for
// the Care Timeline's synthetic one-time-reminder activity -- see
// lib/careTimeline.ts.
const ICON_MAP: Record<string, LucideIcon> = {
  Droplet,
  Droplets,
  Leaf,
  Sprout,
  Flower,
  Flower2,
  Sun,
  Sunrise,
  Scissors,
  Shovel,
  Bug,
  SprayCan,
  Thermometer,
  Wind,
  CloudRain,
  Snowflake,
  Moon,
  Recycle,
  Package,
  Bell,
  Heart,
  Star,
  Sparkles,
  TreeDeciduous,
  TreePine,
  Bird,
  Worm,
  Beaker,
  Pipette,
  Fan,
  Lightbulb,
  Pin,
};

export function getIcon(name?: string | null): LucideIcon | null {
  if (!name) return null;
  return ICON_MAP[name] ?? null;
}
