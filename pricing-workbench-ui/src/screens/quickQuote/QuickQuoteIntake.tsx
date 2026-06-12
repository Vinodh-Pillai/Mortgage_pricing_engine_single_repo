import { type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import type { BorrowerIntake, LaunchState, MetadataState } from '../../lib/api/quoteRuns';
import { QuoteIntakeFlow } from '../quoteIntake';

export default function QuickQuoteIntake({
  intake,
  errors,
  launchState,
  metadataState,
  onChange,
  onRetry,
  onSubmit,
}: {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  launchState: LaunchState;
  metadataState: MetadataState;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
  onRetry: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const navigate = useNavigate();
  return (
    <QuoteIntakeFlow
      intake={intake}
      errors={errors}
      launchState={launchState}
      metadataState={metadataState}
      onChange={onChange}
      onRetry={onRetry}
      onSubmit={onSubmit}
      onNavigate={(route) => navigate(route)}
    />
  );
}
