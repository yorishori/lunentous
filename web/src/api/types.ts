export interface Plant {
  id: number;
  name: string;
  species: string | null;
  location: string | null;
  acquired_date: string | null;
  avatar_photo_id: number | null;
  avatar_photo_path: string | null;
  general_notes: string | null;
  archived: number;
  created_at: string;
  updated_at: string;
}

export interface ReminderType {
  id: number;
  name: string;
  icon: string | null;
  color: string | null;
  archived: number;
  usage_count?: number;
}

export interface PhaseType {
  id: number;
  name: string;
  color: string | null;
  archived: number;
  usage_count?: number;
}

export interface OverridePeriod {
  id?: number;
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  interval_days: number | null;
}

export interface ReminderRule {
  id: number;
  plant_id: number;
  reminder_type_id: number;
  default_interval_days: number | null;
  override_periods: OverridePeriod[];
}

export interface ReminderState {
  id: number;
  plant_id: number;
  reminder_type_id: number;
  due_date: string | null;
  notified: number;
  days_overdue: number | null;
  reminder_type_name?: string;
  reminder_type_icon?: string | null;
  reminder_type_color?: string;
  plant_name?: string;
}

export interface PhaseWindow {
  id: number;
  plant_id: number;
  phase_type_id: number;
  start_month: number;
  start_day: number;
  end_month: number;
  end_day: number;
  notes: string | null;
  phase_type_name?: string;
  phase_type_color?: string;
}

export interface Photo {
  id: number;
  timeline_event_id: number | null;
  file_path: string;
}

export interface TimelineEvent {
  id: number;
  plant_id: number;
  reminder_type_id: number | null;
  event_date: string;
  text: string | null;
  photos: Photo[];
}

export interface PlantDetail extends Plant {
  active_phase_windows: PhaseWindow[];
  reminder_states: ReminderState[];
}

export interface ApiKey {
  id: number;
  label: string | null;
  created_at: string;
}
