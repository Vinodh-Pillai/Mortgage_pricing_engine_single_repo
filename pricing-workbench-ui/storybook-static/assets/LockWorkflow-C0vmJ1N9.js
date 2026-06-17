import{i as e,s as t}from"./preload-helper-xPQekRTU.js";import{t as n}from"./iframe-jOsZz4gJ.js";import{t as r}from"./jsx-runtime-CaZkqeYb.js";import{i,n as a,t as o}from"./quoteRuns-BZ0AIwtY.js";function s({open:e,workflow:t,disclosuresAccepted:n,confirmation:r,confirming:i,onCancel:a,onConfirm:o}){return e?(0,c.jsxs)(`section`,{role:`dialog`,"aria-modal":`true`,"aria-labelledby":`confirm-lock-heading`,className:`panel`,children:[(0,c.jsx)(`h2`,{id:`confirm-lock-heading`,children:`Confirm Lock`}),(0,c.jsx)(`p`,{children:`Confirm backend-returned lock terms and disclosure acceptance before submitting.`}),(0,c.jsxs)(`dl`,{className:`status-grid`,children:[(0,c.jsx)(`dt`,{children:`Note rate`}),(0,c.jsx)(`dd`,{children:t.terms.noteRate}),(0,c.jsx)(`dt`,{children:`Final price`}),(0,c.jsxs)(`dd`,{children:[t.terms.finalPriceBps,` bps`]}),(0,c.jsx)(`dt`,{children:`Expiration`}),(0,c.jsx)(`dd`,{children:new Date(t.terms.expiresAt).toLocaleString()}),(0,c.jsx)(`dt`,{children:`Lock ID preview`}),(0,c.jsx)(`dd`,{children:(0,c.jsx)(`code`,{children:t.lockIdPreview})}),(0,c.jsx)(`dt`,{children:`Disclosures accepted`}),(0,c.jsx)(`dd`,{children:n?`Yes`:`No`})]}),r?.status===`CONFLICT`?(0,c.jsxs)(`div`,{role:`alert`,children:[`Conflict: `,r.conflictResolution??r.message]}):null,(0,c.jsx)(`button`,{type:`button`,disabled:!n||t.lockDisabled||i,onClick:o,children:i?`Confirming...`:`Confirm Lock`}),(0,c.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:a,children:`Cancel`})]}):null}var c,l=e((()=>{c=r(),s.__docgenInfo={description:``,methods:[],displayName:`ConfirmLockDialog`,props:{open:{required:!0,tsType:{name:`boolean`},description:``},workflow:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  selectedOfferId: string;
  status: LockWorkflowStatus;
  lockIdPreview: string;
  lockId: string | null;
  terms: LockWorkflowTerms;
  disclosures: LockWorkflowDisclosure[];
  lockDisabled: boolean;
  lockDisabledReason: string | null;
  blockers: LockWorkflowBlocker[];
  postLockActions: LockWorkflowAction[];
  history: LockWorkflowHistoryEvent[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`selectedOfferId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockIdPreview`,value:{name:`string`,required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`terms`,value:{name:`signature`,type:`object`,raw:`{
  productLabel: string;
  investor: string;
  channel: string;
  noteRate: string;
  finalPriceBps: string;
  lockPeriodDays: number;
  expiresAt: string;
  waterfallRef: string;
  adjustmentRefs: string[];
  marginRefs: string[];
  investorConfirmationRequired: boolean;
}`,signature:{properties:[{key:`productLabel`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`channel`,value:{name:`string`,required:!0}},{key:`noteRate`,value:{name:`string`,required:!0}},{key:`finalPriceBps`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`number`,required:!0}},{key:`expiresAt`,value:{name:`string`,required:!0}},{key:`waterfallRef`,value:{name:`string`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`investorConfirmationRequired`,value:{name:`boolean`,required:!0}}]},required:!0}},{key:`disclosures`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
}`,signature:{properties:[{key:`disclosureId`,value:{name:`string`,required:!0}},{key:`title`,value:{name:`string`,required:!0}},{key:`text`,value:{name:`string`,required:!0}},{key:`complianceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowDisclosure[]`,required:!0}},{key:`lockDisabled`,value:{name:`boolean`,required:!0}},{key:`lockDisabledReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  code: string;
  message: string;
  remediation: string;
  sourceRef: string;
}`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowBlocker[]`,required:!0}},{key:`postLockActions`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}}],raw:`LockWorkflowAction[]`,required:!0}},{key:`history`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowHistoryEvent[]`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``},disclosuresAccepted:{required:!0,tsType:{name:`boolean`},description:``},confirmation:{required:!0,tsType:{name:`union`,raw:`LockConfirmationResult | null`,elements:[{name:`signature`,type:`object`,raw:`{
  status: 'CONFIRMED' | 'CONFLICT' | 'BLOCKED';
  lockId: string | null;
  expiresAt: string | null;
  message: string;
  conflictResolution?: string | null;
  auditRef: string;
  historyEvent?: LockWorkflowHistoryEvent;
}`,signature:{properties:[{key:`status`,value:{name:`union`,raw:`'CONFIRMED' | 'CONFLICT' | 'BLOCKED'`,elements:[{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'CONFLICT'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`expiresAt`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`conflictResolution`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`auditRef`,value:{name:`string`,required:!0}},{key:`historyEvent`,value:{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]},required:!1}}]}},{name:`null`}]},description:``},confirming:{required:!0,tsType:{name:`boolean`},description:``},onCancel:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``},onConfirm:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function u({disclosures:e,scrollComplete:t,checked:n,signatureName:r,onScrollComplete:i,onCheckedChange:a,onSignatureChange:o}){return(0,d.jsxs)(`section`,{className:`panel`,"aria-labelledby":`disclosures-heading`,children:[(0,d.jsx)(`h2`,{id:`disclosures-heading`,children:`Disclosures`}),(0,d.jsxs)(`div`,{"aria-label":`Disclosure text`,tabIndex:0,onScroll:e=>{let t=e.currentTarget;t.scrollTop+t.clientHeight>=t.scrollHeight-2&&i(!0)},style:{maxHeight:`12rem`,overflow:`auto`,border:`1px solid currentColor`,padding:`0.75rem`},children:[e.map(e=>(0,d.jsxs)(`article`,{children:[(0,d.jsx)(`h3`,{children:e.title}),(0,d.jsx)(`p`,{children:e.text}),(0,d.jsxs)(`p`,{children:[(0,d.jsx)(`strong`,{children:`Compliance ref:`}),` `,(0,d.jsx)(`code`,{children:e.complianceRef})]})]},e.disclosureId)),(0,d.jsx)(`p`,{"data-testid":`disclosure-end`,children:`End of disclosures`})]}),(0,d.jsxs)(`p`,{className:`trace-badge`,children:[`Scroll status: `,t?`complete`:`required`]}),(0,d.jsxs)(`label`,{children:[(0,d.jsx)(`input`,{type:`checkbox`,checked:n,disabled:!t,onChange:e=>a(e.currentTarget.checked)}),` I have read and accept the lock disclosures`]}),(0,d.jsxs)(`label`,{children:[`Digital signature`,(0,d.jsx)(`input`,{"aria-label":`Digital signature`,value:r,disabled:!t||!n,onChange:e=>o(e.currentTarget.value),placeholder:`Type signer name`})]}),(0,d.jsxs)(`p`,{children:[`Accept Disclosures status: `,t&&n&&r.trim()?`complete`:`incomplete`]})]})}var d,f=e((()=>{d=r(),u.__docgenInfo={description:``,methods:[],displayName:`DisclosuresPanel`,props:{disclosures:{required:!0,tsType:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
}`,signature:{properties:[{key:`disclosureId`,value:{name:`string`,required:!0}},{key:`title`,value:{name:`string`,required:!0}},{key:`text`,value:{name:`string`,required:!0}},{key:`complianceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowDisclosure[]`},description:``},scrollComplete:{required:!0,tsType:{name:`boolean`},description:``},checked:{required:!0,tsType:{name:`boolean`},description:``},signatureName:{required:!0,tsType:{name:`string`},description:``},onScrollComplete:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(complete: boolean) => void`,signature:{arguments:[{type:{name:`boolean`},name:`complete`}],return:{name:`void`}}},description:``},onCheckedChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(checked: boolean) => void`,signature:{arguments:[{type:{name:`boolean`},name:`checked`}],return:{name:`void`}}},description:``},onSignatureChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(name: string) => void`,signature:{arguments:[{type:{name:`string`},name:`name`}],return:{name:`void`}}},description:``}}}}));function p(e){return[[`eventId`,`eventType`,`timestamp`,`actor`,`terms`,`approvalRef`,`auditRef`],...e.map(e=>[e.eventId,e.eventType,e.timestamp,e.actor,e.terms,e.approvalRef??``,e.auditRef])].map(e=>e.map(e=>`"${String(e).replace(/"/g,`""`)}"`).join(`,`)).join(`
`)}function m(e){let t=p(e),n=`data:text/csv;charset=utf-8,${encodeURIComponent(t)}`,r=document.createElement(`a`);r.href=n,r.download=`lock-history.csv`,r.click()}function h({events:e}){return(0,g.jsxs)(`section`,{className:`panel`,"aria-labelledby":`lock-history-heading`,children:[(0,g.jsx)(`h2`,{id:`lock-history-heading`,children:`Lock History`}),(0,g.jsx)(`ol`,{children:e.map(e=>(0,g.jsxs)(`li`,{children:[(0,g.jsx)(`strong`,{children:e.eventType}),` at `,new Date(e.timestamp).toLocaleString(),` by `,e.actor,(0,g.jsx)(`p`,{children:e.terms}),(0,g.jsxs)(`p`,{children:[`Approval: `,e.approvalRef??`N/A`,` | Audit: `,(0,g.jsx)(`code`,{children:e.auditRef})]})]},e.eventId))}),(0,g.jsx)(`button`,{type:`button`,onClick:()=>m(e),children:`Export History CSV`})]})}var g,_=e((()=>{g=r(),h.__docgenInfo={description:``,methods:[],displayName:`LockHistory`,props:{events:{required:!0,tsType:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowHistoryEvent[]`},description:``}}}}));function v(e,t=new Date){let n=new Date(e).getTime()-t.getTime();if(n<=0)return{severity:`expired`,label:`Expired`,text:`Lock expired`};let r=n/(3600*1e3),i=r>=24?`${Math.ceil(r/24)}d remaining`:`${Math.ceil(r)}h remaining`;return r<=2?{severity:`critical`,label:i,text:`Critical: lock expires within 2 hours`}:r<=24?{severity:`warning`,label:i,text:`Warning: lock expires within 24 hours`}:r<=72?{severity:`info`,label:i,text:`Notice: lock expires within 72 hours`}:{severity:`normal`,label:i,text:`Lock expiration tracked`}}function y(e){return e==null||e===``?`N/A`:String(e)}var b=e((()=>{}));function x({status:e,lockId:t,expiresAt:n,extensionCount:r}){let i=v(n),a=e===`FLOAT_DOWN`?`FLOAT-DOWN`:e;return(0,S.jsxs)(`section`,{className:`banner banner--${i.severity}`,role:`status`,"aria-label":`Lock status banner`,children:[(0,S.jsx)(`strong`,{children:a}),` `,(0,S.jsx)(`span`,{children:i.text}),(0,S.jsxs)(`dl`,{className:`status-grid`,children:[(0,S.jsx)(`dt`,{children:`Lock ID`}),(0,S.jsx)(`dd`,{children:(0,S.jsx)(`code`,{children:y(t)})}),(0,S.jsx)(`dt`,{children:`Countdown`}),(0,S.jsx)(`dd`,{children:i.label}),(0,S.jsx)(`dt`,{children:`Expiration`}),(0,S.jsx)(`dd`,{children:new Date(n).toLocaleString()}),(0,S.jsx)(`dt`,{children:`Extensions`}),(0,S.jsx)(`dd`,{children:r})]})]})}var S,C=e((()=>{b(),S=r(),x.__docgenInfo={description:``,methods:[],displayName:`LockStatusBanner`,props:{status:{required:!0,tsType:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}]},description:``},lockId:{required:!0,tsType:{name:`union`,raw:`string | null | undefined`,elements:[{name:`string`},{name:`null`},{name:`undefined`}]},description:``},expiresAt:{required:!0,tsType:{name:`string`},description:``},extensionCount:{required:!0,tsType:{name:`number`},description:``}}}}));function w({workflow:e,disabled:t,onLock:n}){let{terms:r}=e;return(0,E.jsxs)(`section`,{className:`panel`,"aria-labelledby":`lock-terms-heading`,children:[(0,E.jsx)(`h2`,{id:`lock-terms-heading`,children:`Lock Terms`}),(0,E.jsxs)(`dl`,{className:`status-grid`,children:[(0,E.jsx)(`dt`,{children:`Selected offer`}),(0,E.jsx)(`dd`,{children:(0,E.jsx)(`code`,{children:e.selectedOfferId})}),(0,E.jsx)(`dt`,{children:`Product`}),(0,E.jsx)(`dd`,{children:r.productLabel}),(0,E.jsx)(`dt`,{children:`Investor`}),(0,E.jsx)(`dd`,{children:r.investor}),(0,E.jsx)(`dt`,{children:`Channel`}),(0,E.jsx)(`dd`,{children:r.channel}),(0,E.jsx)(`dt`,{children:`Note rate`}),(0,E.jsx)(`dd`,{children:r.noteRate}),(0,E.jsx)(`dt`,{children:`Final price`}),(0,E.jsxs)(`dd`,{children:[r.finalPriceBps,` bps`]}),(0,E.jsx)(`dt`,{children:`Lock period`}),(0,E.jsxs)(`dd`,{children:[r.lockPeriodDays,` days`]}),(0,E.jsx)(`dt`,{children:`Expiration`}),(0,E.jsx)(`dd`,{children:new Date(r.expiresAt).toLocaleString()}),(0,E.jsx)(`dt`,{children:`Investor confirmation`}),(0,E.jsx)(`dd`,{children:r.investorConfirmationRequired?`Required by backend`:`Not required by backend`}),(0,E.jsx)(`dt`,{children:`Waterfall ref`}),(0,E.jsx)(`dd`,{children:(0,E.jsx)(`code`,{children:r.waterfallRef})})]}),(0,E.jsx)(T,{label:`Adjustment refs`,values:r.adjustmentRefs}),(0,E.jsx)(T,{label:`Margin refs`,values:r.marginRefs}),(0,E.jsx)(`button`,{type:`button`,disabled:t,onClick:n,children:`Lock This Rate`})]})}function T({label:e,values:t}){return(0,E.jsxs)(`div`,{className:`copyable-ref-list`,"aria-label":e,children:[(0,E.jsx)(`strong`,{children:e}),t.length?(0,E.jsx)(`ul`,{children:t.map(e=>(0,E.jsx)(`li`,{children:(0,E.jsx)(`code`,{children:e})},e))}):(0,E.jsx)(`p`,{children:y(null)})]})}var E,D=e((()=>{b(),E=r(),w.__docgenInfo={description:``,methods:[],displayName:`LockTermsPanel`,props:{workflow:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  selectedOfferId: string;
  status: LockWorkflowStatus;
  lockIdPreview: string;
  lockId: string | null;
  terms: LockWorkflowTerms;
  disclosures: LockWorkflowDisclosure[];
  lockDisabled: boolean;
  lockDisabledReason: string | null;
  blockers: LockWorkflowBlocker[];
  postLockActions: LockWorkflowAction[];
  history: LockWorkflowHistoryEvent[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`selectedOfferId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockIdPreview`,value:{name:`string`,required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`terms`,value:{name:`signature`,type:`object`,raw:`{
  productLabel: string;
  investor: string;
  channel: string;
  noteRate: string;
  finalPriceBps: string;
  lockPeriodDays: number;
  expiresAt: string;
  waterfallRef: string;
  adjustmentRefs: string[];
  marginRefs: string[];
  investorConfirmationRequired: boolean;
}`,signature:{properties:[{key:`productLabel`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`channel`,value:{name:`string`,required:!0}},{key:`noteRate`,value:{name:`string`,required:!0}},{key:`finalPriceBps`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`number`,required:!0}},{key:`expiresAt`,value:{name:`string`,required:!0}},{key:`waterfallRef`,value:{name:`string`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`investorConfirmationRequired`,value:{name:`boolean`,required:!0}}]},required:!0}},{key:`disclosures`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
}`,signature:{properties:[{key:`disclosureId`,value:{name:`string`,required:!0}},{key:`title`,value:{name:`string`,required:!0}},{key:`text`,value:{name:`string`,required:!0}},{key:`complianceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowDisclosure[]`,required:!0}},{key:`lockDisabled`,value:{name:`boolean`,required:!0}},{key:`lockDisabledReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  code: string;
  message: string;
  remediation: string;
  sourceRef: string;
}`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowBlocker[]`,required:!0}},{key:`postLockActions`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}}],raw:`LockWorkflowAction[]`,required:!0}},{key:`history`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowHistoryEvent[]`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``},disabled:{required:!0,tsType:{name:`boolean`},description:``},onLock:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function O({status:e,actions:t,onSelect:n}){return(0,k.jsxs)(`section`,{className:`panel`,"aria-labelledby":`post-lock-actions-heading`,children:[(0,k.jsx)(`h2`,{id:`post-lock-actions-heading`,children:`Post-Lock Actions`}),(0,k.jsxs)(`p`,{children:[`Available after confirmation or represented as backend eligibility evidence. Current state: `,e]}),(0,k.jsx)(`ul`,{className:`offer-list`,children:t.map(e=>(0,k.jsxs)(`li`,{children:[(0,k.jsx)(`strong`,{children:e.label}),` `,(0,k.jsx)(`span`,{className:`trace-badge`,children:e.eligible?`eligible`:`blocked`}),(0,k.jsx)(`p`,{children:e.terms}),(0,k.jsxs)(`p`,{children:[`Fee: `,y(e.fee),` | Max days: `,y(e.maxDays),` | Approval: `,e.approvalRequired?`required`:`not required`]}),e.blocker?(0,k.jsx)(`p`,{role:`alert`,children:e.blocker}):null,(0,k.jsx)(`button`,{type:`button`,disabled:!e.eligible,onClick:()=>n(e),children:e.label})]},e.action))})]})}var k,A=e((()=>{b(),k=r(),O.__docgenInfo={description:``,methods:[],displayName:`PostLockActions`,props:{status:{required:!0,tsType:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}]},description:``},actions:{required:!0,tsType:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}}],raw:`LockWorkflowAction[]`},description:``},onSelect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(action: LockWorkflowAction) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}},name:`action`}],return:{name:`void`}}},description:``}}}}));function j({workflow:e,onReturn:t}){return(0,M.jsx)(`main`,{className:`quote-lock-screen`,"aria-labelledby":`blocked-lock-heading`,children:(0,M.jsxs)(`section`,{className:`panel`,role:`alert`,"aria-labelledby":`blocked-lock-heading`,children:[(0,M.jsx)(`h1`,{id:`blocked-lock-heading`,children:`Lock workflow blocked`}),(0,M.jsx)(`p`,{children:e.lockDisabledReason??`Backend lock workflow is unavailable.`}),(0,M.jsx)(`ul`,{children:e.blockers.map(e=>(0,M.jsxs)(`li`,{children:[(0,M.jsx)(`strong`,{children:e.code}),`: `,e.message,(0,M.jsx)(`p`,{children:e.remediation}),(0,M.jsx)(`code`,{children:e.sourceRef})]},e.code))}),(0,M.jsx)(`button`,{type:`button`,onClick:t,children:`Return to Offers`})]})})}var M,N=e((()=>{M=r(),j.__docgenInfo={description:``,methods:[],displayName:`BlockedLock`,props:{workflow:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  selectedOfferId: string;
  status: LockWorkflowStatus;
  lockIdPreview: string;
  lockId: string | null;
  terms: LockWorkflowTerms;
  disclosures: LockWorkflowDisclosure[];
  lockDisabled: boolean;
  lockDisabledReason: string | null;
  blockers: LockWorkflowBlocker[];
  postLockActions: LockWorkflowAction[];
  history: LockWorkflowHistoryEvent[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`selectedOfferId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockIdPreview`,value:{name:`string`,required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`terms`,value:{name:`signature`,type:`object`,raw:`{
  productLabel: string;
  investor: string;
  channel: string;
  noteRate: string;
  finalPriceBps: string;
  lockPeriodDays: number;
  expiresAt: string;
  waterfallRef: string;
  adjustmentRefs: string[];
  marginRefs: string[];
  investorConfirmationRequired: boolean;
}`,signature:{properties:[{key:`productLabel`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`channel`,value:{name:`string`,required:!0}},{key:`noteRate`,value:{name:`string`,required:!0}},{key:`finalPriceBps`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`number`,required:!0}},{key:`expiresAt`,value:{name:`string`,required:!0}},{key:`waterfallRef`,value:{name:`string`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`investorConfirmationRequired`,value:{name:`boolean`,required:!0}}]},required:!0}},{key:`disclosures`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
}`,signature:{properties:[{key:`disclosureId`,value:{name:`string`,required:!0}},{key:`title`,value:{name:`string`,required:!0}},{key:`text`,value:{name:`string`,required:!0}},{key:`complianceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowDisclosure[]`,required:!0}},{key:`lockDisabled`,value:{name:`boolean`,required:!0}},{key:`lockDisabledReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  code: string;
  message: string;
  remediation: string;
  sourceRef: string;
}`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowBlocker[]`,required:!0}},{key:`postLockActions`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}}],raw:`LockWorkflowAction[]`,required:!0}},{key:`history`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowHistoryEvent[]`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``},onReturn:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}})),P,F,I=e((()=>{P={tenantContext:`tenant-fixture`,runId:`run-preview-001`,selectedOfferId:`offer-a`,status:`READY`,lockIdPreview:`lock-preview-offer-a`,lockId:null,terms:{productLabel:`Conventional 30 year fixed`,investor:`Backend Investor A`,channel:`Retail`,noteRate:`6.500%`,finalPriceBps:`101.125`,lockPeriodDays:45,expiresAt:new Date(Date.now()+1560*60*1e3).toISOString(),waterfallRef:`waterfall:offer-a:final`,adjustmentRefs:[`adjustment:llpa:backend-ref`,`adjustment:state:backend-ref`],marginRefs:[`margin:retail:backend-ref`],investorConfirmationRequired:!0},disclosures:[{disclosureId:`lock-disclosure-fixture`,title:`Rate lock disclosure`,text:`Borrower and loan officer acknowledge that lock terms, fees, expiration, and post-lock options are controlled by backend lock, quote, investor, and compliance services. This local screen renders returned values only and does not calculate rates or eligibility.`,complianceRef:`compliance:rate-lock:fixture`}],lockDisabled:!1,lockDisabledReason:null,blockers:[],postLockActions:[{action:`extend`,label:`Extend Lock`,eligible:!0,fee:`$125`,maxDays:15,approvalRequired:!0,terms:`Backend returned one extension option for this lock.`},{action:`relock`,label:`Relock`,eligible:!0,fee:`$0`,maxDays:null,approvalRequired:!0,terms:`Backend returned relock eligibility for current market terms.`},{action:`float_down`,label:`Float Down`,eligible:!1,fee:null,maxDays:null,approvalRequired:!0,terms:`Backend did not return float-down eligibility.`,blocker:`FLOAT_DOWN_NOT_ELIGIBLE`}],history:[{eventId:`evt-created`,eventType:`created`,timestamp:`2026-06-11T18:00:00Z`,actor:`loan-officer-fixture`,terms:`Workflow created from selected offer offer-a.`,approvalRef:null,auditRef:`audit:lock-created`}],uiTraceId:`pii-24-s12-local-trace`,events:[`lock.workflow.fixture.loaded`],fallbackReason:`Local deterministic fixture used when lock-service and compliance-service endpoints are unavailable.`},{...P},{...P},[...P.history],F={status:`CONFIRMED`,lockId:`lock-confirmed-offer-a`,expiresAt:P.terms.expiresAt,message:`Lock confirmed from deterministic fixture.`,conflictResolution:null,auditRef:`audit:lock-confirmed`,historyEvent:{eventId:`evt-confirmed`,eventType:`confirmed`,timestamp:`2026-06-11T18:05:00Z`,actor:`loan-officer-fixture`,terms:`Lock confirmed with backend returned terms.`,approvalRef:`approval:investor:pending`,auditRef:`audit:lock-confirmed`}}}));function L({tenantId:e=`tenant-fixture`,runId:t,optionId:n,uiTraceId:r=`pii-24-s12-local-trace`,workflow:i=P,confirmLock:c,fetchImpl:l,onEvidenceCapture:d,onNavigate:f}){let[p,m]=(0,z.useState)(i),g=t??i.runId,_=n??i.selectedOfferId,[v,b]=(0,z.useState)(p.status),[S,C]=(0,z.useState)(!1),[T,E]=(0,z.useState)(!1),[D,k]=(0,z.useState)(``),[A,M]=(0,z.useState)(!1),[N,I]=(0,z.useState)(p.lockId?{...F,lockId:p.lockId,expiresAt:p.terms.expiresAt}:null),[L,V]=(0,z.useState)(null),[H,U]=(0,z.useState)(!1),W=R({...p,status:v}),G=S&&T&&D.trim().length>0;(0,z.useEffect)(()=>{m(i),b(i.status)},[i]),(0,z.useEffect)(()=>{let t=!1;async function n(){try{let n=await a(e,g,_,l);t||(m(n),b(n.status),n.lockId&&I({...F,lockId:n.lockId,expiresAt:n.terms.expiresAt}))}catch{t||m(e=>e??P)}}if(n(),v!==`CONFIRMED`)return()=>{t=!0};let r=window.setInterval(()=>void n(),3e4);return()=>{t=!0,window.clearInterval(r)}},[_,g,v,l,e]),(0,z.useEffect)(()=>{d?.({screenId:`quote-lock`,timestamp:new Date().toISOString(),state:W,dataRefs:[e,g,_,p.uiTraceId,r,p.terms.waterfallRef],blockers:p.lockDisabled?p.blockers.map(e=>e.code):[]})},[_,g,d,e,r,W,p]);async function K(t=`confirm`){let n={selectedOfferId:_,action:t,disclosuresAccepted:G,disclosureScrollComplete:S,signatureName:D.trim(),signedAt:new Date().toISOString()};U(!0);try{let r=await(c?.(n)??o(e,g,n,l).catch(()=>F));I(r),r.status===`CONFIRMED`&&b(t===`confirm`?`CONFIRMED`:t===`float_down`?`FLOAT_DOWN`:t===`relock`?`RELOCKED`:`EXTENDED`),M(!1),V(null)}finally{U(!1)}}function q(e){f?.(e)}return p.lockDisabled||v===`BLOCKED`?(0,B.jsx)(j,{workflow:p,onReturn:()=>q(`/quote/${encodeURIComponent(g)}/offers`)}):(0,B.jsxs)(`main`,{className:`quote-lock-screen`,"aria-labelledby":`quote-lock-title`,children:[(0,B.jsxs)(`section`,{className:`hero`,"aria-labelledby":`quote-lock-title`,children:[(0,B.jsx)(`p`,{className:`eyebrow`,children:`Lock workflow | PII-24-S12`}),(0,B.jsx)(`h1`,{id:`quote-lock-title`,children:`Lock Workflow`}),(0,B.jsx)(`p`,{children:`Review backend supplied terms, complete disclosures, confirm lock, and manage post-lock actions without local pricing decisions.`}),(0,B.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>q(`/quote/${encodeURIComponent(g)}/offers`),children:`Back to offers`})]}),(0,B.jsx)(x,{status:v,lockId:N?.lockId??p.lockId,expiresAt:N?.expiresAt??p.terms.expiresAt,extensionCount:p.history.filter(e=>e.eventType===`extended`).length}),(0,B.jsxs)(`section`,{className:`quote-detail-layout`,"aria-label":`Responsive lock workflow layout`,style:{display:`grid`,gap:`1rem`,gridTemplateColumns:`repeat(auto-fit, minmax(min(100%, 22rem), 1fr))`},children:[(0,B.jsxs)(`div`,{children:[(0,B.jsx)(w,{workflow:p,onLock:()=>M(!0),disabled:!G||H}),(0,B.jsx)(u,{disclosures:p.disclosures,scrollComplete:S,checked:T,signatureName:D,onScrollComplete:C,onCheckedChange:E,onSignatureChange:k})]}),(0,B.jsxs)(`div`,{children:[(0,B.jsx)(O,{status:v,actions:p.postLockActions,onSelect:e=>V(e)}),(0,B.jsx)(h,{events:N?.historyEvent?[...p.history,N.historyEvent]:p.history})]})]}),(0,B.jsx)(s,{open:A,workflow:p,disclosuresAccepted:G,confirmation:N,confirming:H,onCancel:()=>M(!1),onConfirm:()=>void K(`confirm`)}),L?(0,B.jsxs)(`section`,{role:`dialog`,"aria-modal":`true`,"aria-labelledby":`post-lock-action-heading`,className:`panel`,children:[(0,B.jsx)(`h2`,{id:`post-lock-action-heading`,children:L.label}),(0,B.jsx)(`p`,{children:L.terms}),(0,B.jsxs)(`dl`,{className:`status-grid`,children:[(0,B.jsx)(`dt`,{children:`Fee`}),(0,B.jsx)(`dd`,{children:y(L.fee)}),(0,B.jsx)(`dt`,{children:`Max days`}),(0,B.jsx)(`dd`,{children:y(L.maxDays)}),(0,B.jsx)(`dt`,{children:`Approval required`}),(0,B.jsx)(`dd`,{children:L.approvalRequired?`Yes`:`No`})]}),(0,B.jsxs)(`button`,{type:`button`,disabled:!L.eligible||H,onClick:()=>void K(L.action),children:[`Confirm `,L.label]}),(0,B.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>V(null),children:`Cancel`})]}):null]})}function R(e){return!e.terms||e.disclosures.length===0?`empty`:e.lockDisabled||e.status===`BLOCKED`?`blocked`:e.status===`READY`?`ready`:`needs-attention`}var z,B;e((()=>{z=t(n(),1),i(),l(),f(),_(),C(),D(),A(),N(),I(),b(),B=r(),L.__docgenInfo={description:``,methods:[],displayName:`QuoteLockScreen`,props:{workflow:{required:!1,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  selectedOfferId: string;
  status: LockWorkflowStatus;
  lockIdPreview: string;
  lockId: string | null;
  terms: LockWorkflowTerms;
  disclosures: LockWorkflowDisclosure[];
  lockDisabled: boolean;
  lockDisabledReason: string | null;
  blockers: LockWorkflowBlocker[];
  postLockActions: LockWorkflowAction[];
  history: LockWorkflowHistoryEvent[];
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`selectedOfferId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'EXPIRED'`},{name:`literal`,value:`'EXTENDED'`},{name:`literal`,value:`'RELOCKED'`},{name:`literal`,value:`'FLOAT_DOWN'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockIdPreview`,value:{name:`string`,required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`terms`,value:{name:`signature`,type:`object`,raw:`{
  productLabel: string;
  investor: string;
  channel: string;
  noteRate: string;
  finalPriceBps: string;
  lockPeriodDays: number;
  expiresAt: string;
  waterfallRef: string;
  adjustmentRefs: string[];
  marginRefs: string[];
  investorConfirmationRequired: boolean;
}`,signature:{properties:[{key:`productLabel`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`channel`,value:{name:`string`,required:!0}},{key:`noteRate`,value:{name:`string`,required:!0}},{key:`finalPriceBps`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`number`,required:!0}},{key:`expiresAt`,value:{name:`string`,required:!0}},{key:`waterfallRef`,value:{name:`string`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`investorConfirmationRequired`,value:{name:`boolean`,required:!0}}]},required:!0}},{key:`disclosures`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  disclosureId: string;
  title: string;
  text: string;
  complianceRef: string;
}`,signature:{properties:[{key:`disclosureId`,value:{name:`string`,required:!0}},{key:`title`,value:{name:`string`,required:!0}},{key:`text`,value:{name:`string`,required:!0}},{key:`complianceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowDisclosure[]`,required:!0}},{key:`lockDisabled`,value:{name:`boolean`,required:!0}},{key:`lockDisabledReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  code: string;
  message: string;
  remediation: string;
  sourceRef: string;
}`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowBlocker[]`,required:!0}},{key:`postLockActions`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  action: LockWorkflowActionType;
  label: string;
  eligible: boolean;
  fee: string | null;
  maxDays: number | null;
  approvalRequired: boolean;
  terms: string;
  blocker?: string | null;
}`,signature:{properties:[{key:`action`,value:{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`eligible`,value:{name:`boolean`,required:!0}},{key:`fee`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`maxDays`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!0}},{key:`approvalRequired`,value:{name:`boolean`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`blocker`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]}}],raw:`LockWorkflowAction[]`,required:!0}},{key:`history`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`LockWorkflowHistoryEvent[]`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``,defaultValue:{value:`{
  tenantContext: 'tenant-fixture',
  runId: 'run-preview-001',
  selectedOfferId: 'offer-a',
  status: 'READY',
  lockIdPreview: 'lock-preview-offer-a',
  lockId: null,
  terms: {
    productLabel: 'Conventional 30 year fixed',
    investor: 'Backend Investor A',
    channel: 'Retail',
    noteRate: '6.500%',
    finalPriceBps: '101.125',
    lockPeriodDays: 45,
    expiresAt: new Date(Date.now() + 26 * 60 * 60 * 1000).toISOString(),
    waterfallRef: 'waterfall:offer-a:final',
    adjustmentRefs: ['adjustment:llpa:backend-ref', 'adjustment:state:backend-ref'],
    marginRefs: ['margin:retail:backend-ref'],
    investorConfirmationRequired: true,
  },
  disclosures: [
    {
      disclosureId: 'lock-disclosure-fixture',
      title: 'Rate lock disclosure',
      text: 'Borrower and loan officer acknowledge that lock terms, fees, expiration, and post-lock options are controlled by backend lock, quote, investor, and compliance services. This local screen renders returned values only and does not calculate rates or eligibility.',
      complianceRef: 'compliance:rate-lock:fixture',
    },
  ],
  lockDisabled: false,
  lockDisabledReason: null,
  blockers: [],
  postLockActions: [
    { action: 'extend', label: 'Extend Lock', eligible: true, fee: '$125', maxDays: 15, approvalRequired: true, terms: 'Backend returned one extension option for this lock.' },
    { action: 'relock', label: 'Relock', eligible: true, fee: '$0', maxDays: null, approvalRequired: true, terms: 'Backend returned relock eligibility for current market terms.' },
    { action: 'float_down', label: 'Float Down', eligible: false, fee: null, maxDays: null, approvalRequired: true, terms: 'Backend did not return float-down eligibility.', blocker: 'FLOAT_DOWN_NOT_ELIGIBLE' },
  ],
  history: [
    { eventId: 'evt-created', eventType: 'created', timestamp: '2026-06-11T18:00:00Z', actor: 'loan-officer-fixture', terms: 'Workflow created from selected offer offer-a.', approvalRef: null, auditRef: 'audit:lock-created' },
  ],
  uiTraceId: 'pii-24-s12-local-trace',
  events: ['lock.workflow.fixture.loaded'],
  fallbackReason: 'Local deterministic fixture used when lock-service and compliance-service endpoints are unavailable.',
}`,computed:!1}},confirmLock:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(request: LockConfirmationRequest) => Promise<LockConfirmationResult> | LockConfirmationResult`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  selectedOfferId: string;
  action: 'confirm' | LockWorkflowActionType;
  disclosuresAccepted: boolean;
  disclosureScrollComplete: boolean;
  signatureName: string;
  signedAt: string;
}`,signature:{properties:[{key:`selectedOfferId`,value:{name:`string`,required:!0}},{key:`action`,value:{name:`union`,raw:`'confirm' | LockWorkflowActionType`,elements:[{name:`literal`,value:`'confirm'`},{name:`union`,raw:`'extend' | 'relock' | 'float_down'`,elements:[{name:`literal`,value:`'extend'`},{name:`literal`,value:`'relock'`},{name:`literal`,value:`'float_down'`}]}],required:!0}},{key:`disclosuresAccepted`,value:{name:`boolean`,required:!0}},{key:`disclosureScrollComplete`,value:{name:`boolean`,required:!0}},{key:`signatureName`,value:{name:`string`,required:!0}},{key:`signedAt`,value:{name:`string`,required:!0}}]}},name:`request`}],return:{name:`union`,raw:`Promise<LockConfirmationResult> | LockConfirmationResult`,elements:[{name:`Promise`,elements:[{name:`signature`,type:`object`,raw:`{
  status: 'CONFIRMED' | 'CONFLICT' | 'BLOCKED';
  lockId: string | null;
  expiresAt: string | null;
  message: string;
  conflictResolution?: string | null;
  auditRef: string;
  historyEvent?: LockWorkflowHistoryEvent;
}`,signature:{properties:[{key:`status`,value:{name:`union`,raw:`'CONFIRMED' | 'CONFLICT' | 'BLOCKED'`,elements:[{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'CONFLICT'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`expiresAt`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`conflictResolution`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`auditRef`,value:{name:`string`,required:!0}},{key:`historyEvent`,value:{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]},required:!1}}]}}],raw:`Promise<LockConfirmationResult>`},{name:`signature`,type:`object`,raw:`{
  status: 'CONFIRMED' | 'CONFLICT' | 'BLOCKED';
  lockId: string | null;
  expiresAt: string | null;
  message: string;
  conflictResolution?: string | null;
  auditRef: string;
  historyEvent?: LockWorkflowHistoryEvent;
}`,signature:{properties:[{key:`status`,value:{name:`union`,raw:`'CONFIRMED' | 'CONFLICT' | 'BLOCKED'`,elements:[{name:`literal`,value:`'CONFIRMED'`},{name:`literal`,value:`'CONFLICT'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`lockId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`expiresAt`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`conflictResolution`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`auditRef`,value:{name:`string`,required:!0}},{key:`historyEvent`,value:{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestamp: string;
  actor: string;
  terms: string;
  approvalRef: string | null;
  auditRef: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestamp`,value:{name:`string`,required:!0}},{key:`actor`,value:{name:`string`,required:!0}},{key:`terms`,value:{name:`string`,required:!0}},{key:`approvalRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]},required:!1}}]}}]}}},description:``},fetchImpl:{required:!1,tsType:{name:`fetch`},description:``},onNavigate:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(path: string) => void`,signature:{arguments:[{type:{name:`string`},name:`path`}],return:{name:`void`}}},description:``},tenantId:{defaultValue:{value:`'tenant-fixture'`,computed:!1},required:!1},uiTraceId:{defaultValue:{value:`'pii-24-s12-local-trace'`,computed:!1},required:!1}}}}))();export{v as countdownWarning,L as default,R as stateForLockWorkflow,y as valueText};