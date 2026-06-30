import type { OfferComparisonView, OfferSummary } from '../../lib/api/offers';

export type OfferSortField = 'rank' | 'rate' | 'apr' | 'payment' | 'confidence' | 'rankScore';
export type SortDirection = 'asc' | 'desc';

export type OfferFilters = {
  productFamily: string;
  investor: string;
  rateMax: string;
  confidenceMin: string;
  lockPeriodDays: string;
  eligibilityStatus: string;
};

export type OfferSort = {
  field: OfferSortField;
  direction: SortDirection;
};

export const defaultOfferFilters: OfferFilters = {
  productFamily: '',
  investor: '',
  rateMax: '',
  confidenceMin: '',
  lockPeriodDays: '',
  eligibilityStatus: '',
};

export const defaultOfferSort: OfferSort = { field: 'rank', direction: 'asc' };

export function sortOffers(offers: OfferSummary[], sort: OfferSort): OfferSummary[] {
  return offers
    .map((offer, index) => ({ offer, index }))
    .sort((left, right) => {
      const result = compareOfferValues(left.offer, right.offer, sort.field);
      return (sort.direction === 'asc' ? result : -result) || left.offer.rank - right.offer.rank || left.index - right.index;
    })
    .map(({ offer }) => offer);
}

export function filterOffers(offers: OfferSummary[], filters: OfferFilters): OfferSummary[] {
  const maxRate = numericFilter(filters.rateMax);
  const minConfidence = numericFilter(filters.confidenceMin);
  return offers.filter((offer) => {
    if (filters.productFamily && normalized(offer.productFamily ?? offer.productLabel) !== normalized(filters.productFamily)) return false;
    if (filters.investor && normalized(offer.investor) !== normalized(filters.investor)) return false;
    if (filters.lockPeriodDays && normalized(offer.lockPeriodDays) !== normalized(filters.lockPeriodDays)) return false;
    if (filters.eligibilityStatus && normalized(offer.eligibilityStatus) !== normalized(filters.eligibilityStatus)) return false;
    const rate = numericValue(offer.rate);
    if (maxRate !== null && rate !== null && rate > maxRate) return false;
    const confidence = numericValue(offer.confidence);
    if (minConfidence !== null && confidence !== null && confidence < minConfidence) return false;
    return true;
  });
}

export function offerOptions(offers: OfferSummary[], selector: (offer: OfferSummary) => string | number | null | undefined): string[] {
  return Array.from(new Set(offers.map((offer) => valueText(selector(offer))).filter((value) => value !== 'N/A'))).sort();
}

export function toggleCompareOffer(ids: string[], offerId: string, max = 4): string[] {
  if (ids.includes(offerId)) return ids.filter((id) => id !== offerId);
  return ids.length >= max ? ids : [...ids, offerId];
}

export function stateForComparison(comparison: OfferComparisonView): 'empty' | 'blocked' | 'ready' {
  if (comparison.commitBlocked && comparison.offers.length === 0) return 'blocked';
  if (comparison.offers.length === 0) return 'empty';
  return 'ready';
}

export function valueText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'N/A';
  return String(value);
}

export function offerDisplayName(offer: OfferSummary) {
  return valueText(offer.productLabel ?? offer.offerId);
}

export function visibleOfferEvidenceValues(values: string[] | null | undefined): string[] {
  return (values ?? [])
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => !isInternalOfferEvidence(value));
}

export function visibleOfferReferenceValues(values: string[] | null | undefined): string[] {
  return visibleOfferEvidenceValues(values)
    .filter((value) => !/^https?:\/\//i.test(value))
    .filter((value) => !/^source[_\s:-]/i.test(value));
}

function compareOfferValues(left: OfferSummary, right: OfferSummary, field: OfferSortField) {
  const leftValue = offerValue(left, field);
  const rightValue = offerValue(right, field);
  if (leftValue < rightValue) return -1;
  if (leftValue > rightValue) return 1;
  return 0;
}

function offerValue(offer: OfferSummary, field: OfferSortField) {
  const raw = field === 'rank' ? offer.rank : offer[field];
  return numericValue(raw) ?? valueText(raw).toLowerCase();
}

function numericFilter(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function numericValue(value: string | number | null | undefined) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string') return null;
  const parsed = Number(value.replace(/[%,$]/g, ''));
  return Number.isFinite(parsed) ? parsed : null;
}

function isInternalOfferEvidence(value: string) {
  return /loan\s*pass|loan\s*house|quick\s*pricer|quickpricer|source\s*url|source_url|rank\b|ranked|rank\s*score|confidence|schema\s*version|schemaVersion|https?:\/\//i.test(value);
}

function normalized(value: string | number | null | undefined) {
  return valueText(value).toLowerCase();
}
