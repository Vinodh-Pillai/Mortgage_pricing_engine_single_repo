import { useTranslation } from '../lib/i18n';

export function Footer() {
  const { t } = useTranslation('common');
  return (
    <footer className="layout-footer" role="contentinfo">
      <span>LoanWeft v0.1.0</span>
      <span aria-label="Shell status">{t('responsiveShellReady')}</span>
    </footer>
  );
}
