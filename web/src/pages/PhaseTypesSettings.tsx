import TypeManager from "../components/TypeManager";

export default function PhaseTypesSettings() {
  return (
    <div>
      <h1>Phase Types</h1>
      <TypeManager basePath="/phase-types" hasIcon={false} queryKey="phase-types" />
    </div>
  );
}
