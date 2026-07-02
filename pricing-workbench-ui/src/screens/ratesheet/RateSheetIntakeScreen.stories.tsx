import { RateSheetIntakeScreen } from './RateSheetIntakeScreen';

export default { title: 'PII-25/Functionality Pages/Rate Sheet Intake' };

export function AllVisualStates() {
  return <RateSheetIntakeScreen />;
}

export function DesktopDark() { return <RateSheetIntakeScreen />; }
export function MobileResponsive() { return <div style={{ maxWidth: 390 }}><RateSheetIntakeScreen /></div>; }
