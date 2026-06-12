import type { BorrowerIntake, ScenarioIntakeField } from '../../../lib/api/quoteRuns';
import { StepFields } from './StepFields';
import type { IntakeFieldErrors } from '../validation';

export function Step6Preferences({ fields, intake, errors, onChange }: { fields: ScenarioIntakeField[]; intake: BorrowerIntake; errors: IntakeFieldErrors; onChange: (field: keyof BorrowerIntake, value: string) => void }) {
  return <StepFields fields={fields} intake={intake} errors={errors} onChange={onChange} />;
}
