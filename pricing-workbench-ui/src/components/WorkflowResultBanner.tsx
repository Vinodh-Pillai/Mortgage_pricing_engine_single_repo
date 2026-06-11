import React from 'react';
import { ChipList } from './ChipList';
import type { TenantSetupResult } from '../lib/api/tenants';
import type { ProductSetupResult } from '../lib/api/products';

type ResultType = TenantSetupResult | ProductSetupResult;

export function WorkflowResultBanner({ result, successLabel, blockedLabel }: { result: ResultType; successLabel: string; blockedLabel: string }) {
  const accepted = result.status === 'RECORDED';
  return (
    <div className={accepted ? 'banner banner--success' : 'banner banner--blocked'} role={accepted ? 'status' : 'alert'}>
      <strong>{accepted ? successLabel : blockedLabel}</strong>
      <span>{result.message}</span>
      <span>{result.nextStep}</span>
      <ChipList label="Setup notes" values={result.placeholders} />
    </div>
  );
}
