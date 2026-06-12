import { useParams } from 'react-router-dom';

export type PricingWorkbenchRouteParams = {
  runId?: string;
  optionId?: string;
  caseId?: string;
};

export function useRouteParams<TParams extends Record<string, string | undefined> = PricingWorkbenchRouteParams>() {
  return useParams<TParams>();
}
