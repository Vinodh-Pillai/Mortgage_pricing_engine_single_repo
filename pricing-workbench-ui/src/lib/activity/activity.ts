export type ActivityAction =
  | 'pipeline_created'
  | 'pipeline_viewed'
  | 'quote_launched'
  | 'lock_requested'
  | 'rate_sheet_uploaded'
  | 'pricing_analyzed';

export type ActivityEntityType = 'pipeline' | 'quote' | 'lock' | 'rate_sheet' | 'pricing_analysis';

export type ActivityStatus = 'ACTIVE' | 'PENDING' | 'LOCKED' | 'CLOSED' | 'NEEDS_ATTENTION';

export type ActivityRecord = {
  id: string;
  userId: string;
  action: ActivityAction;
  entityType: ActivityEntityType;
  entityId: string;
  borrowerName: string;
  propertyAddress: string;
  status: ActivityStatus;
  lastAction: string;
  timestamp: string;
  route: string;
  metadata?: Record<string, string | number | boolean | null | undefined>;
};

export type ActivityInput = {
  borrowerName?: string;
  propertyAddress?: string;
  status?: ActivityStatus;
  route?: string;
  timestamp?: string;
};

export const activityStoragePrefix = 'loanweft:activity:';

export function activityStorageKey(userId: string) {
  return `${activityStoragePrefix}${userId}`;
}

export function recordActivity(
  userId: string,
  action: ActivityAction,
  entityType: ActivityEntityType,
  entityId: string,
  metadata: ActivityInput & Record<string, string | number | boolean | null | undefined> = {},
): ActivityRecord {
  const timestamp = typeof metadata.timestamp === 'string' ? metadata.timestamp : new Date().toISOString();
  const record: ActivityRecord = {
    id: `${entityType}:${entityId}:${timestamp}`,
    userId,
    action,
    entityType,
    entityId,
    borrowerName: typeof metadata.borrowerName === 'string' ? metadata.borrowerName : 'Pipeline record',
    propertyAddress: typeof metadata.propertyAddress === 'string' ? metadata.propertyAddress : 'Address pending',
    status: isActivityStatus(metadata.status) ? metadata.status : 'ACTIVE',
    lastAction: actionLabel(action),
    timestamp,
    route: typeof metadata.route === 'string' ? metadata.route : routeForActivity(entityType, entityId),
    metadata,
  };

  const current = readStoredActivity(userId);
  writeStoredActivity(userId, [record, ...current]);
  return record;
}

export function getRecentActivity(userId: string, limit = 5): ActivityRecord[] {
  const seen = new Set<string>();
  return readStoredActivity(userId)
    .sort((left, right) => Date.parse(right.timestamp) - Date.parse(left.timestamp))
    .filter((record) => {
      const key = `${record.entityType}:${record.entityId}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, limit);
}

export function seedRecentActivity(userId: string, records: ActivityRecord[]) {
  writeStoredActivity(userId, records);
}

export function actionLabel(action: ActivityAction) {
  const labels: Record<ActivityAction, string> = {
    pipeline_created: 'Pipeline created',
    pipeline_viewed: 'Pipeline viewed',
    quote_launched: 'Quote launched',
    lock_requested: 'Lock requested',
    rate_sheet_uploaded: 'Rate sheet uploaded',
    pricing_analyzed: 'Pricing analyzed',
  };
  return labels[action];
}

function readStoredActivity(userId: string): ActivityRecord[] {
  if (typeof window === 'undefined') return [];
  try {
    const parsed = JSON.parse(window.localStorage.getItem(activityStorageKey(userId)) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter(isActivityRecord) : [];
  } catch {
    return [];
  }
}

function writeStoredActivity(userId: string, records: ActivityRecord[]) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(activityStorageKey(userId), JSON.stringify(records.slice(0, 25)));
}

function routeForActivity(entityType: ActivityEntityType, entityId: string) {
  return entityType === 'pipeline' || entityType === 'quote' ? `/quote/${encodeURIComponent(entityId)}/offers` : `/${entityType}s`;
}

function isActivityStatus(value: unknown): value is ActivityStatus {
  return value === 'ACTIVE' || value === 'PENDING' || value === 'LOCKED' || value === 'CLOSED' || value === 'NEEDS_ATTENTION';
}

function isActivityRecord(value: unknown): value is ActivityRecord {
  const candidate = value as ActivityRecord;
  return Boolean(candidate?.userId && candidate.entityId && candidate.timestamp && candidate.route);
}
