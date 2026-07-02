import { PricingAnalysisScreen } from './PricingAnalysisScreen';

export default { title: 'PII-25/Functionality Pages/Pricing Analysis' };

export function AllVisualStates() {
  return <PricingAnalysisScreen runId="run-test" />;
}

export function DesktopDark() { return <PricingAnalysisScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><PricingAnalysisScreen /></div>; }
