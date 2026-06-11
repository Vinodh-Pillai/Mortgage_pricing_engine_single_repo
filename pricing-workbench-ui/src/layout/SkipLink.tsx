import { useTranslation } from '../lib/i18n';

export function SkipLink() {
  const { t } = useTranslation('common');
  return <a className="layout-skip-link" href="#main-content">{t('skipToMainContent')}</a>;
}
