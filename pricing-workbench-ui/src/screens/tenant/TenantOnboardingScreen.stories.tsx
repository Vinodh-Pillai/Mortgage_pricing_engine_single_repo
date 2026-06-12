import { TenantOnboardingScreen } from './TenantOnboardingScreen';
import type { ScreenVisualState } from '../contract/ScreenProps';

export default { title: 'PII-25/Functionality Pages/Tenant Onboarding' };

const states: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function AllVisualStates() {
  return <>{states.map((state) => <div key={state} style={{ marginBottom: 24 }}><TenantOnboardingScreen visualState={state} /></div>)}</>;
}

export function DesktopDark() { return <TenantOnboardingScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><TenantOnboardingScreen /></div>; }
