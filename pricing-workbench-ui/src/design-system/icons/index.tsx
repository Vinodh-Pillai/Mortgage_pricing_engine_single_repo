import { forwardRef, type ComponentPropsWithoutRef } from 'react';
import { Icon as PrimitiveIcon } from '../primitives';

export { PrimitiveIcon as Icon };

export const iconSizes = {
  sm: 16,
  md: 20,
  lg: 24,
} as const;

type IconProps = Omit<ComponentPropsWithoutRef<typeof PrimitiveIcon>, 'children'>;
type IconPathProps = IconProps & { path: string; secondaryPath?: string };

const PathIcon = forwardRef<SVGSVGElement, IconPathProps>(function PathIcon({ path, secondaryPath, ...props }, ref) {
  return (
    <PrimitiveIcon ref={ref} {...props}>
      <path d={path} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      {secondaryPath ? <path d={secondaryPath} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /> : null}
    </PrimitiveIcon>
  );
});

function makeIcon(path: string, secondaryPath?: string) {
  return forwardRef<SVGSVGElement, IconProps>(function DesignSystemIcon(props, ref) {
    return <PathIcon ref={ref} path={path} secondaryPath={secondaryPath} {...props} />;
  });
}

export const LoanOfficerIcon = makeIcon('M4 19V8l8-4 8 4v11M8 19v-7h8v7');
export const PricingAnalystIcon = makeIcon('M4 18h16M7 15V9M12 15V5M17 15v-3');
export const OperationsLeadIcon = makeIcon('M4 7h16M7 7v10h10V7M9 11h6');
export const GovernanceReviewerIcon = makeIcon('M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7l8-4z');
export const AdminIcon = makeIcon('M12 8a4 4 0 100 8 4 4 0 000-8z', 'M4 12h2M18 12h2M12 4v2M12 18v2M6.5 6.5l1.4 1.4M16.1 16.1l1.4 1.4M17.5 6.5l-1.4 1.4M7.9 16.1l-1.4 1.4');
export const PartnerManagerIcon = makeIcon('M7 11a3 3 0 100-6 3 3 0 000 6zM17 11a3 3 0 100-6 3 3 0 000 6zM4 20a5 5 0 0110 0M10 20a5 5 0 0110 0');
export const ComplianceOfficerIcon = makeIcon('M5 12l4 4L19 6');
export const BorrowerIcon = makeIcon('M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0');

export const HomeIcon = makeIcon('M4 11l8-7 8 7v9H5v-9');
export const QuoteIcon = makeIcon('M7 7h10M7 11h6M6 19h12a2 2 0 002-2V5a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z');
export const PricingIcon = makeIcon('M12 2v20M17 5H9.5a3.5 3.5 0 000 7H14a3.5 3.5 0 010 7H6');
export const LockIcon = makeIcon('M7 11V8a5 5 0 0110 0v3M6 11h12v10H6z');
export const PartnerIcon = PartnerManagerIcon;
export const OpsIcon = OperationsLeadIcon;
export const ComplianceIcon = ComplianceOfficerIcon;
export const SettingsIcon = AdminIcon;

export const SuccessIcon = makeIcon('M5 13l4 4L19 7');
export const WarningIcon = makeIcon('M12 3l10 18H2L12 3z', 'M12 9v4M12 17h.01');
export const ErrorIcon = makeIcon('M6 6l12 12M18 6L6 18');
export const InfoIcon = makeIcon('M12 17v-6M12 7h.01M12 22a10 10 0 100-20 10 10 0 000 20z');
export const HelpIcon = InfoIcon;
export const BlockedIcon = makeIcon('M6 6l12 12', 'M12 22a10 10 0 100-20 10 10 0 000 20z');
export const PendingIcon = makeIcon('M12 6v6l4 2M12 22a10 10 0 100-20 10 10 0 000 20z');

export const SearchIcon = makeIcon('M11 19a8 8 0 100-16 8 8 0 000 16zM21 21l-4.3-4.3');
export const FilterIcon = makeIcon('M4 5h16M7 12h10M10 19h4');
export const SortIcon = makeIcon('M8 7l4-4 4 4M12 3v18M16 17l-4 4-4-4');
export const DownloadIcon = makeIcon('M12 3v12M7 10l5 5 5-5M5 21h14');
export const UploadIcon = makeIcon('M12 21V9M7 14l5-5 5 5M5 3h14');
export const EditIcon = makeIcon('M4 20h4L19 9l-4-4L4 16v4z');
export const DeleteIcon = makeIcon('M5 7h14M10 11v6M14 11v6M8 7l1-3h6l1 3M7 7l1 14h8l1-14');
export const CopyIcon = makeIcon('M8 8h12v12H8zM4 16V4h12');
export const ShareIcon = makeIcon('M18 8a3 3 0 100-6 3 3 0 000 6zM6 15a3 3 0 100-6 3 3 0 000 6zM18 22a3 3 0 100-6 3 3 0 000 6z', 'M8.6 13.5l6.8-3M8.6 16.5l6.8 3');
