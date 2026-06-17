import { afterEach, describe, expect, it } from 'vitest';
import { activityStorageKey, getRecentActivity, recordActivity, seedRecentActivity, type ActivityRecord } from './activity';

afterEach(() => {
  window.localStorage.clear();
});

describe('ActivityTest', () => {
  it('ActivityTest.recordsAndRetrieves', () => {
    recordActivity('user-1', 'pipeline_created', 'pipeline', 'run-1', {
      borrowerName: 'Johnson',
      propertyAddress: '456 Oak Ave',
      status: 'ACTIVE',
      timestamp: '2026-06-10T14:30:00Z',
    });

    const activity = getRecentActivity('user-1');

    expect(activity).toHaveLength(1);
    expect(activity[0]).toMatchObject({ borrowerName: 'Johnson', route: '/quote/run-1/offers' });
    expect(window.localStorage.getItem(activityStorageKey('user-1'))).toContain('run-1');
  });

  it('returns the latest five unique records', () => {
    const records = Array.from({ length: 7 }, (_, index): ActivityRecord => ({
      id: `pipeline:run-${index}:2026-06-10T14:3${index}:00Z`,
      userId: 'user-1',
      action: 'pipeline_viewed',
      entityType: 'pipeline',
      entityId: `run-${index === 0 ? 1 : index}`,
      borrowerName: `Borrower ${index}`,
      propertyAddress: `${index} Main St`,
      status: 'ACTIVE',
      lastAction: 'Pipeline viewed',
      timestamp: `2026-06-10T14:3${index}:00Z`,
      route: `/quote/run-${index}/offers`,
    }));

    seedRecentActivity('user-1', records);

    expect(getRecentActivity('user-1')).toHaveLength(5);
    expect(getRecentActivity('user-1')[0].entityId).toBe('run-6');
    expect(getRecentActivity('user-1').some((record) => record.entityId === 'run-0')).toBe(false);
  });
});
