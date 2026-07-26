import TypeManager from "../components/TypeManager";

export default function ReminderTypesSettings() {
  return (
    <div>
      <h1>Reminder Types</h1>
      <TypeManager basePath="/reminder-types" hasIcon queryKey="reminder-types" noun="Reminder type" />
    </div>
  );
}
