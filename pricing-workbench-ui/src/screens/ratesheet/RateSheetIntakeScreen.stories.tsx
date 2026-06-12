import { RateSheetIntakeScreen } from './RateSheetIntakeScreen';
import type { ScreenVisualState } from '../contract/ScreenProps';

export default { title: 'PII-25/Functionality Pages/Rate Sheet Intake' };

const states: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export function AllVisualStates() {
  return <>{states.map((state) => <div key={state} style={{ marginBottom: 24 }}><RateSheetIntakeScreen visualState={state} /></div>)}</>;
}

export function DesktopDark() { return <RateSheetIntakeScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><RateSheetIntakeScreen /></div>; }
