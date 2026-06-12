import { PricingAnalysisScreen } from './PricingAnalysisScreen';
import type { ScreenVisualState } from '../contract/ScreenProps';

export default { title: 'PII-25/Functionality Pages/Pricing Analysis' };

const states: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function AllVisualStates() {
  return <>{states.map((state) => <div key={state} style={{ marginBottom: 24 }}><PricingAnalysisScreen visualState={state} /></div>)}</>;
}

export function DesktopDark() { return <PricingAnalysisScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><PricingAnalysisScreen /></div>; }
