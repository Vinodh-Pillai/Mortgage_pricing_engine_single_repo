import{i as e,s as t}from"./preload-helper-xPQekRTU.js";import{t as n}from"./iframe-jOsZz4gJ.js";import{t as r}from"./jsx-runtime-CaZkqeYb.js";import{i,r as a}from"./quoteRuns-BZ0AIwtY.js";function o(e){return Array.from({length:e},(e,t)=>{let n=t+1,r=s[t%s.length],i=c[t%c.length],a=n===7||n===118;return{ordinal:n,section:r,step:r===`Adjustments`?`Adjustment ${i} step ${n}`:`${r} step ${n}`,inputValue:a?{value:null,redacted:!0,reason:`MARGIN_CONFIDENTIAL`,auditRef:`audit:redaction-001`}:{value:`backend-input-${n}`,redacted:!1,reason:null},operation:`BACKEND_${r.toUpperCase().replace(/\s+/g,`_`)}`,outputValue:a?{value:null,redacted:!0,reason:`MARGIN_CONFIDENTIAL`,auditRef:`audit:redaction-001`}:{value:`backend-output-${n}`,redacted:!1,reason:null},configRef:r===`Margins`?`margin-service:branch-margin-001`:r===`Adjustments`?`adjustment-service:adj-001`:`pricing-service:grid-config-001`,reasonCode:a?`REDACTED_MARGIN`:r===`Adjustments`?i:r.toUpperCase().replace(/\s+/g,`_`),roundingMode:r===`Rounding`?`nearest-eighth-from-backend`:null,inputDetails:r===`Adjustments`?[`Backend input ref ${n}`,`Reason code ${i}`,`Conditions supplied by adjustment-service for row ${n}`]:[`Backend input ref ${n}`,`No local pricing calculation for row ${n}`],outputDetails:[`Backend output ref ${n}`],adjustmentRefs:r===`Adjustments`?[`adjustment-service:${i.toLowerCase()}`]:[],marginRefs:r===`Margins`?[`margin-service:branch-margin-001`]:[]}})}var s,c,l,u=e((()=>{s=[`Base Rate`,`Adjustments`,`Margins`,`Rounding`],c=[`FICO_720_739`,`LTV_80_85`,`CASH_OUT_REFI`],l={tenantContext:`tenant-fixture`,runId:`run-preview-001`,status:`READY`,restrictedValuesVisible:!1,dependencyStatus:`fixture-backed`,baseSelection:{selectionId:`base-selection-standalone-001`,gridVersionRef:`grid:v2026-06-waterfall-fixture`,selectedNoteRate:{value:`6.500%`,redacted:!1,reason:null},basePrice:{value:`101.125`,redacted:!1,reason:null},ledgerSteps:[`base-grid`,`adjustments`,`margin`,`rounding`]},finalPrice:{finalPriceId:`final-price-standalone-001`,roundedFinalPrice:{value:`100.875`,redacted:!1,reason:null},adjustmentRefs:[`adjustment-service:adj-001`,`adjustment-service:adj-redacted-002`],marginRefs:[`margin-service:branch-margin-001`],roundingTraceRefs:[`pricing-service:rounding-trace-001`],roundingMode:`nearest-eighth-from-backend`,precision:`0.125`,ledger:o(220)},blockers:[],versionRefs:[`pricing-config:v2026-06`,`margin-config:v2026-06`,`adjustment-config:v2026-06`],auditRefs:[`audit:waterfall-001`,`audit:redaction-001`,`audit:pricing-replay-001`],replayHash:`replay-hash-waterfall-001`,versionGraphHash:`version-graph-hash-waterfall-001`,resultHash:`result-hash-waterfall-001`,evidenceHash:`evidence-hash-waterfall-001`,uiTraceId:`pii-24-s14-waterfall-fixture`,events:[`fixture:pricing-waterfall-rendered`,`fixture:redaction-metadata-present`],fallbackReason:``},{...l}}));function d({tenantId:e=`tenant-fixture`,runId:t,uiTraceId:n=`pii-24-s14-local-trace`,waterfall:r,fetchImpl:i,onEvidenceCapture:o,onNavigate:s}){let c=t??r?.runId??l.runId,[u,d]=(0,M.useState)(()=>({kind:`loaded`,waterfall:r??l})),p=u.kind===`loaded`||u.kind===`blocked`?u.waterfall:l,m=w(u);return(0,M.useEffect)(()=>{if(r||!i)return;let t=!1;return d({kind:`loading`}),a(e,c,i).then(e=>{t||d({kind:`loaded`,waterfall:e})}).catch(e=>{t||d({kind:`blocked`,message:e.message,waterfall:{...l,runId:c,status:`BLOCKED`}})}),()=>{t=!0}},[c,i,e,r]),(0,M.useEffect)(()=>{o?.({screenId:`pricing-waterfall`,timestamp:new Date().toISOString(),state:m,dataRefs:[e,c,p.uiTraceId,n,p.replayHash,p.evidenceHash,P],blockers:u.kind===`blocked`?[u.message,...p.blockers.map(e=>e.code)]:p.blockers.map(e=>e.code)})},[c,p,u,o,e,n,m]),u.kind===`loading`?(0,N.jsx)(`main`,{className:`pricing-waterfall-screen`,"aria-busy":`true`,children:(0,N.jsx)(`section`,{className:`panel`,children:(0,N.jsx)(`p`,{role:`status`,children:`Loading pricing waterfall sections...`})})}):(0,N.jsx)(f,{waterfall:p,visualState:m,onNavigate:s})}function f({waterfall:e,visualState:t,onNavigate:n}){let[r,i]=(0,M.useState)(`all`),[a,o]=(0,M.useState)(``),[s,c]=(0,M.useState)(``),[l,u]=(0,M.useState)(new Set),[d,f]=(0,M.useState)(``),h=(0,M.useMemo)(()=>D(e.finalPrice.ledger),[e.finalPrice.ledger]),y=(0,M.useMemo)(()=>e.finalPrice.ledger.filter(e=>{let t=r===`all`||O(e)===r,n=!a||e.reasonCode.toLowerCase().includes(a.toLowerCase()),i=!s||e.configRef.toLowerCase().includes(s.toLowerCase());return t&&n&&i}),[s,a,r,e.finalPrice.ledger]),b=e.finalPrice.ledger.filter(e=>O(e)===`Adjustments`).length;function x(e){u(t=>{let n=new Set(t);return n.has(e)?n.delete(e):n.add(e),n})}function w(){u(new Set(y.map(e=>e.ordinal)))}function T(e){n?.(e)}return(0,N.jsxs)(`main`,{className:`pricing-waterfall-screen`,"aria-labelledby":`pricing-waterfall-title`,children:[(0,N.jsxs)(`header`,{className:`hero`,style:{position:`sticky`,top:0,zIndex:1},children:[(0,N.jsx)(`p`,{className:`eyebrow`,children:`Pricing waterfall | PII-24-S14`}),(0,N.jsx)(`h1`,{id:`pricing-waterfall-title`,children:`Pricing Waterfall`}),(0,N.jsxs)(`p`,{children:[`Run `,(0,N.jsx)(`code`,{children:e.runId}),`. The screen renders pricing-service data, refs, hashes, and redaction metadata without local pricing calculation.`]}),(0,N.jsxs)(`div`,{role:`toolbar`,"aria-label":`Waterfall exports`,children:[(0,N.jsx)(`button`,{type:`button`,onClick:()=>f(C(e)),children:`Export CSV`}),` `,(0,N.jsx)(`button`,{type:`button`,onClick:()=>f(S(e)),children:`Export JSON`}),` `,(0,N.jsx)(`button`,{type:`button`,disabled:!0,title:`PDF export requires approved local PDF tooling`,children:`Export PDF unavailable`})]})]}),t===`blocked`?(0,N.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:`Pricing waterfall is blocked by backend evidence boundaries.`}):null,E(e)?(0,N.jsx)(`div`,{className:`banner banner--info`,children:`Redacted backend values include reason and audit refs.`}):null,(0,N.jsxs)(`section`,{className:`quote-detail-layout`,"aria-label":`Pricing waterfall layout`,style:{display:`grid`,gap:`1rem`,gridTemplateColumns:`repeat(auto-fit, minmax(min(100%, 22rem), 1fr))`},children:[(0,N.jsx)(p,{waterfall:e,totalAdjustmentRows:b,onNavigate:T}),(0,N.jsx)(g,{waterfall:e,onNavigate:T}),(0,N.jsx)(_,{waterfall:e,onNavigate:T}),(0,N.jsx)(v,{waterfall:e,onNavigate:T})]}),(0,N.jsx)(m,{groups:h,rows:y,expandedSteps:l,sectionFilter:r,reasonFilter:a,configFilter:s,onSectionFilter:i,onReasonFilter:o,onConfigFilter:c,onToggleRow:x,onExpandAll:w,onCollapseAll:()=>u(new Set),onNavigate:T}),d?(0,N.jsx)(`textarea`,{"aria-label":`Exported pricing waterfall`,readOnly:!0,value:d,rows:12}):null]})}function p({waterfall:e,totalAdjustmentRows:t,onNavigate:n}){return(0,N.jsxs)(`section`,{className:`panel`,"aria-labelledby":`base-selection-heading`,children:[(0,N.jsx)(`h2`,{id:`base-selection-heading`,children:`Base Selection`}),(0,N.jsxs)(`dl`,{className:`status-grid`,children:[(0,N.jsx)(`dt`,{children:`Grid version`}),(0,N.jsx)(`dd`,{children:(0,N.jsx)(b,{value:e.baseSelection.gridVersionRef,onNavigate:n})}),(0,N.jsx)(`dt`,{children:`Selected note rate`}),(0,N.jsx)(`dd`,{children:x(e.baseSelection.selectedNoteRate,n)}),(0,N.jsx)(`dt`,{children:`Base price`}),(0,N.jsx)(`dd`,{children:x(e.baseSelection.basePrice,n)}),(0,N.jsx)(`dt`,{children:`Ledger steps`}),(0,N.jsx)(`dd`,{children:e.finalPrice.ledger.length}),(0,N.jsx)(`dt`,{children:`Total adjustment rows`}),(0,N.jsx)(`dd`,{children:t})]})]})}function m(e){let{groups:t,rows:n,expandedSteps:r,sectionFilter:i,reasonFilter:a,configFilter:o,onSectionFilter:s,onReasonFilter:c,onConfigFilter:l,onToggleRow:u,onExpandAll:d,onCollapseAll:f,onNavigate:p}=e;return(0,N.jsxs)(`section`,{className:`panel`,"aria-labelledby":`ledger-heading`,children:[(0,N.jsx)(`h2`,{id:`ledger-heading`,children:`Ledger`}),(0,N.jsx)(`div`,{className:`status-grid`,"aria-label":`Ledger grouping summary`,children:Array.from(t.entries()).map(([e,t])=>(0,N.jsxs)(N.Fragment,{children:[(0,N.jsx)(`dt`,{children:e},`${e}-label`),(0,N.jsxs)(`dd`,{children:[t.length,` rows`]},`${e}-count`)]}))}),(0,N.jsxs)(`div`,{role:`search`,"aria-label":`Ledger filters`,children:[(0,N.jsxs)(`label`,{children:[`Group by section `,(0,N.jsxs)(`select`,{value:i,onChange:e=>s(e.target.value),children:[(0,N.jsx)(`option`,{value:`all`,children:`All`}),Array.from(t.keys()).map(e=>(0,N.jsx)(`option`,{value:e,children:e},e))]})]}),` `,(0,N.jsxs)(`label`,{children:[`Reason code `,(0,N.jsx)(`input`,{value:a,onChange:e=>c(e.target.value)})]}),` `,(0,N.jsxs)(`label`,{children:[`Config ref `,(0,N.jsx)(`input`,{value:o,onChange:e=>l(e.target.value)})]}),` `,(0,N.jsx)(`button`,{type:`button`,onClick:d,children:`Expand All`}),` `,(0,N.jsx)(`button`,{type:`button`,onClick:f,children:`Collapse All`})]}),(0,N.jsx)(`div`,{style:{maxHeight:`34rem`,overflow:`auto`},"aria-label":`Virtualized ledger viewport`,children:(0,N.jsxs)(`table`,{className:`ds-table`,"aria-label":`Pricing waterfall ledger`,children:[(0,N.jsx)(`thead`,{children:(0,N.jsxs)(`tr`,{children:[(0,N.jsx)(`th`,{scope:`col`,children:`Ordinal`}),(0,N.jsx)(`th`,{scope:`col`,children:`Step`}),(0,N.jsx)(`th`,{scope:`col`,children:`Input Value`}),(0,N.jsx)(`th`,{scope:`col`,children:`Operation`}),(0,N.jsx)(`th`,{scope:`col`,children:`Output Value`}),(0,N.jsx)(`th`,{scope:`col`,children:`Config Ref`}),(0,N.jsx)(`th`,{scope:`col`,children:`Reason Code`}),(0,N.jsx)(`th`,{scope:`col`,children:`Rounding Mode`})]})}),(0,N.jsx)(`tbody`,{children:n.map(e=>(0,N.jsx)(h,{row:e,expanded:r.has(e.ordinal),onToggle:()=>u(e.ordinal),onNavigate:p},e.ordinal))})]})})]})}function h({row:e,expanded:t,onToggle:n,onNavigate:r}){return(0,N.jsxs)(N.Fragment,{children:[(0,N.jsxs)(`tr`,{children:[(0,N.jsx)(`td`,{children:(0,N.jsx)(`button`,{type:`button`,"aria-expanded":t,onClick:n,children:e.ordinal})}),(0,N.jsx)(`td`,{children:e.step}),(0,N.jsx)(`td`,{children:x(e.inputValue,r)}),(0,N.jsx)(`td`,{children:e.operation}),(0,N.jsx)(`td`,{children:x(e.outputValue,r)}),(0,N.jsx)(`td`,{children:(0,N.jsx)(b,{value:e.configRef,onNavigate:r})}),(0,N.jsx)(`td`,{children:e.reasonCode}),(0,N.jsx)(`td`,{children:A(e.roundingMode)})]}),t?(0,N.jsx)(`tr`,{children:(0,N.jsx)(`td`,{colSpan:8,children:(0,N.jsx)(y,{label:`Step ${e.ordinal} details`,values:[...e.inputDetails??[],...e.outputDetails??[],...e.adjustmentRefs??[],...e.marginRefs??[]]})})}):null]})}function g({waterfall:e,onNavigate:t}){return(0,N.jsxs)(`section`,{className:`panel`,"aria-labelledby":`final-price-heading`,children:[(0,N.jsx)(`h2`,{id:`final-price-heading`,children:`Final Price`}),(0,N.jsxs)(`dl`,{className:`status-grid`,children:[(0,N.jsx)(`dt`,{children:`Rounded final price`}),(0,N.jsx)(`dd`,{children:x(e.finalPrice.roundedFinalPrice,t)}),(0,N.jsx)(`dt`,{children:`Rounding mode`}),(0,N.jsx)(`dd`,{children:A(e.finalPrice.roundingMode)}),(0,N.jsx)(`dt`,{children:`Precision`}),(0,N.jsx)(`dd`,{children:A(e.finalPrice.precision)})]}),(0,N.jsx)(y,{label:`Ledger step refs`,values:e.finalPrice.ledger.slice(0,8).map(e=>`step:${e.ordinal}:${e.reasonCode}`)}),(0,N.jsx)(y,{label:`Adjustment refs`,values:e.finalPrice.adjustmentRefs,onNavigate:t}),(0,N.jsx)(y,{label:`Margin refs`,values:e.finalPrice.marginRefs??[],onNavigate:t}),(0,N.jsx)(y,{label:`Rounding trace refs`,values:e.finalPrice.roundingTraceRefs,onNavigate:t})]})}function _({waterfall:e,onNavigate:t}){return(0,N.jsxs)(`section`,{className:`panel`,"aria-labelledby":`blockers-heading`,children:[(0,N.jsx)(`h2`,{id:`blockers-heading`,children:`Blockers`}),e.blockers.length===0?(0,N.jsx)(`p`,{children:`No blockers returned.`}):(0,N.jsx)(`ul`,{children:e.blockers.map(e=>(0,N.jsxs)(`li`,{children:[(0,N.jsx)(`strong`,{children:e.code}),(0,N.jsx)(`p`,{children:e.message}),(0,N.jsx)(b,{value:e.sourceRef,onNavigate:t}),e.remediation?(0,N.jsx)(`p`,{children:e.remediation}):null]},`${e.code}-${e.sourceRef}`))})]})}function v({waterfall:e,onNavigate:t}){return(0,N.jsxs)(`section`,{className:`panel`,"aria-labelledby":`evidence-heading`,children:[(0,N.jsx)(`h2`,{id:`evidence-heading`,children:`Evidence Refs`}),(0,N.jsxs)(`dl`,{className:`status-grid`,children:[(0,N.jsx)(`dt`,{children:`Replay hash`}),(0,N.jsx)(`dd`,{children:(0,N.jsx)(`button`,{type:`button`,onClick:()=>t(`/audit/replay?ref=${encodeURIComponent(e.replayHash)}`),children:e.replayHash})}),(0,N.jsx)(`dt`,{children:`Evidence hash`}),(0,N.jsx)(`dd`,{children:(0,N.jsx)(`code`,{children:e.evidenceHash})}),(0,N.jsx)(`dt`,{children:`Result hash`}),(0,N.jsx)(`dd`,{children:(0,N.jsx)(`code`,{children:e.resultHash})}),(0,N.jsx)(`dt`,{children:`Version graph hash`}),(0,N.jsx)(`dd`,{children:(0,N.jsx)(`code`,{children:e.versionGraphHash})})]}),(0,N.jsx)(y,{label:`Version refs`,values:e.versionRefs,onNavigate:t}),(0,N.jsx)(y,{label:`Audit refs`,values:e.auditRefs,onNavigate:t})]})}function y({label:e,values:t,onNavigate:n}){return(0,N.jsxs)(`div`,{className:`copyable-ref-list`,"aria-label":e,children:[(0,N.jsx)(`strong`,{children:e}),t.length===0?(0,N.jsx)(`p`,{children:`N/A`}):(0,N.jsx)(`ul`,{children:t.map(e=>(0,N.jsx)(`li`,{children:n?(0,N.jsx)(b,{value:e,onNavigate:n}):(0,N.jsx)(`code`,{children:e})},e))})]})}function b({value:e,onNavigate:t}){return(0,N.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>t(k(e)),children:(0,N.jsx)(`code`,{children:e})})}function x(e,t){if(!e.redacted)return(0,N.jsx)(`span`,{children:A(e.value)});let n=e.reason??`REDACTED_BY_BACKEND`,r=e.auditRef??`audit-ref-unavailable`;return(0,N.jsxs)(`span`,{title:`Reason: ${n}; audit ref: ${r}`,children:[(0,N.jsx)(`strong`,{children:`[REDACTED]`}),` `,(0,N.jsxs)(`small`,{children:[n,` | `,(0,N.jsx)(`button`,{type:`button`,onClick:()=>t(`/audit/replay?ref=${encodeURIComponent(r)}`),children:r})]})]})}function S(e){return JSON.stringify({waterfall:e,redactions:T(e)},null,2)}function C(e){return[`ordinal,section,step,input,redactedInputReason,inputAuditRef,operation,output,redactedOutputReason,outputAuditRef,configRef,reasonCode,roundingMode`,...e.finalPrice.ledger.map(e=>[e.ordinal,O(e),e.step,A(e.inputValue.value),e.inputValue.redacted?A(e.inputValue.reason):``,A(e.inputValue.auditRef),e.operation,A(e.outputValue.value),e.outputValue.redacted?A(e.outputValue.reason):``,A(e.outputValue.auditRef),e.configRef,e.reasonCode,A(e.roundingMode)].map(j).join(`,`))].join(`
`)}function w(e){return e.kind===`loading`?`loading`:e.kind===`blocked`||e.waterfall.status===`BLOCKED`?`blocked`:e.waterfall.finalPrice.ledger.length===0?`empty`:E(e.waterfall)||e.waterfall.replayHash?`needs-attention`:`ready`}function T(e){return e.finalPrice.ledger.flatMap(e=>[{ordinal:e.ordinal,field:`inputValue`,reason:e.inputValue.reason,auditRef:e.inputValue.auditRef,redacted:e.inputValue.redacted},{ordinal:e.ordinal,field:`outputValue`,reason:e.outputValue.reason,auditRef:e.outputValue.auditRef,redacted:e.outputValue.redacted}]).filter(e=>e.redacted)}function E(e){return e.finalPrice.ledger.some(e=>e.inputValue.redacted||e.outputValue.redacted)||e.baseSelection.selectedNoteRate.redacted||e.baseSelection.basePrice.redacted||e.finalPrice.roundedFinalPrice.redacted}function D(e){return e.reduce((e,t)=>e.set(O(t),[...e.get(O(t))??[],t]),new Map)}function O(e){return e.section??(e.operation.includes(`ROUNDING`)?`Rounding`:e.operation.includes(`MARGIN`)?`Margins`:e.operation.includes(`ADJUSTMENT`)?`Adjustments`:`Base Rate`)}function k(e){return e.startsWith(`adjustment-service:`)?`/pricing/adjustments?ref=${encodeURIComponent(e)}`:e.startsWith(`margin-service:`)?`/pricing/margins?ref=${encodeURIComponent(e)}`:e.startsWith(`audit:`)?`/audit/replay?ref=${encodeURIComponent(e)}`:e.startsWith(`pricing-service:rounding`)?`/quote/rounding?ref=${encodeURIComponent(e)}`:`/governance/config?ref=${encodeURIComponent(e)}`}function A(e){return e==null||e===``?`N/A`:String(e)}function j(e){return`"${A(e).replace(/"/g,`""`)}"`}var M,N,P;e((()=>{M=t(n(),1),i(),u(),N=r(),P=`.local-harness/evidence/PII-24-S14/pricing-waterfall.json`,d.__docgenInfo={description:``,methods:[],displayName:`PricingWaterfallScreen`,props:{waterfall:{required:!1,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  status: 'READY' | 'BLOCKED';
  restrictedValuesVisible: boolean;
  dependencyStatus: string;
  baseSelection: {
    selectionId: string;
    gridVersionRef: string;
    selectedNoteRate: RedactedWaterfallValue;
    basePrice: RedactedWaterfallValue;
    ledgerSteps: string[];
  };
  finalPrice: {
    finalPriceId: string;
    roundedFinalPrice: RedactedWaterfallValue;
    ledger: WaterfallLedgerRow[];
    adjustmentRefs: string[];
    marginRefs?: string[];
    roundingTraceRefs: string[];
    roundingMode?: string | null;
    precision?: string | null;
  };
  blockers: Array<{ code: string; message: string; sourceRef: string; remediation?: string }>;
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  versionGraphHash: string;
  resultHash: string;
  evidenceHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`restrictedValuesVisible`,value:{name:`boolean`,required:!0}},{key:`dependencyStatus`,value:{name:`string`,required:!0}},{key:`baseSelection`,value:{name:`signature`,type:`object`,raw:`{
  selectionId: string;
  gridVersionRef: string;
  selectedNoteRate: RedactedWaterfallValue;
  basePrice: RedactedWaterfallValue;
  ledgerSteps: string[];
}`,signature:{properties:[{key:`selectionId`,value:{name:`string`,required:!0}},{key:`gridVersionRef`,value:{name:`string`,required:!0}},{key:`selectedNoteRate`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`basePrice`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`ledgerSteps`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]},required:!0}},{key:`finalPrice`,value:{name:`signature`,type:`object`,raw:`{
  finalPriceId: string;
  roundedFinalPrice: RedactedWaterfallValue;
  ledger: WaterfallLedgerRow[];
  adjustmentRefs: string[];
  marginRefs?: string[];
  roundingTraceRefs: string[];
  roundingMode?: string | null;
  precision?: string | null;
}`,signature:{properties:[{key:`finalPriceId`,value:{name:`string`,required:!0}},{key:`roundedFinalPrice`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`ledger`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  ordinal: number;
  section?: 'Base Rate' | 'Adjustments' | 'Margins' | 'Rounding' | string;
  step: string;
  inputValue: RedactedWaterfallValue;
  operation: string;
  outputValue: RedactedWaterfallValue;
  configRef: string;
  reasonCode: string;
  roundingMode: string | null;
  inputDetails?: string[];
  outputDetails?: string[];
  marginRefs?: string[];
  adjustmentRefs?: string[];
}`,signature:{properties:[{key:`ordinal`,value:{name:`number`,required:!0}},{key:`section`,value:{name:`union`,raw:`'Base Rate' | 'Adjustments' | 'Margins' | 'Rounding' | string`,elements:[{name:`literal`,value:`'Base Rate'`},{name:`literal`,value:`'Adjustments'`},{name:`literal`,value:`'Margins'`},{name:`literal`,value:`'Rounding'`},{name:`string`}],required:!1}},{key:`step`,value:{name:`string`,required:!0}},{key:`inputValue`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`operation`,value:{name:`string`,required:!0}},{key:`outputValue`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`configRef`,value:{name:`string`,required:!0}},{key:`reasonCode`,value:{name:`string`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`inputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`outputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`WaterfallLedgerRow[]`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`roundingTraceRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`precision`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{ code: string; message: string; sourceRef: string; remediation?: string }`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!1}}]}}],raw:`Array<{ code: string; message: string; sourceRef: string; remediation?: string }>`,required:!0}},{key:`versionRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`auditRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`replayHash`,value:{name:`string`,required:!0}},{key:`versionGraphHash`,value:{name:`string`,required:!0}},{key:`resultHash`,value:{name:`string`,required:!0}},{key:`evidenceHash`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``},fetchImpl:{required:!1,tsType:{name:`fetch`},description:``},onNavigate:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(path: string) => void`,signature:{arguments:[{type:{name:`string`},name:`path`}],return:{name:`void`}}},description:``},tenantId:{defaultValue:{value:`'tenant-fixture'`,computed:!1},required:!1},uiTraceId:{defaultValue:{value:`'pii-24-s14-local-trace'`,computed:!1},required:!1}}},f.__docgenInfo={description:``,methods:[],displayName:`WaterfallLayout`,props:{waterfall:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  status: 'READY' | 'BLOCKED';
  restrictedValuesVisible: boolean;
  dependencyStatus: string;
  baseSelection: {
    selectionId: string;
    gridVersionRef: string;
    selectedNoteRate: RedactedWaterfallValue;
    basePrice: RedactedWaterfallValue;
    ledgerSteps: string[];
  };
  finalPrice: {
    finalPriceId: string;
    roundedFinalPrice: RedactedWaterfallValue;
    ledger: WaterfallLedgerRow[];
    adjustmentRefs: string[];
    marginRefs?: string[];
    roundingTraceRefs: string[];
    roundingMode?: string | null;
    precision?: string | null;
  };
  blockers: Array<{ code: string; message: string; sourceRef: string; remediation?: string }>;
  versionRefs: string[];
  auditRefs: string[];
  replayHash: string;
  versionGraphHash: string;
  resultHash: string;
  evidenceHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'READY' | 'BLOCKED'`,elements:[{name:`literal`,value:`'READY'`},{name:`literal`,value:`'BLOCKED'`}],required:!0}},{key:`restrictedValuesVisible`,value:{name:`boolean`,required:!0}},{key:`dependencyStatus`,value:{name:`string`,required:!0}},{key:`baseSelection`,value:{name:`signature`,type:`object`,raw:`{
  selectionId: string;
  gridVersionRef: string;
  selectedNoteRate: RedactedWaterfallValue;
  basePrice: RedactedWaterfallValue;
  ledgerSteps: string[];
}`,signature:{properties:[{key:`selectionId`,value:{name:`string`,required:!0}},{key:`gridVersionRef`,value:{name:`string`,required:!0}},{key:`selectedNoteRate`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`basePrice`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`ledgerSteps`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]},required:!0}},{key:`finalPrice`,value:{name:`signature`,type:`object`,raw:`{
  finalPriceId: string;
  roundedFinalPrice: RedactedWaterfallValue;
  ledger: WaterfallLedgerRow[];
  adjustmentRefs: string[];
  marginRefs?: string[];
  roundingTraceRefs: string[];
  roundingMode?: string | null;
  precision?: string | null;
}`,signature:{properties:[{key:`finalPriceId`,value:{name:`string`,required:!0}},{key:`roundedFinalPrice`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`ledger`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  ordinal: number;
  section?: 'Base Rate' | 'Adjustments' | 'Margins' | 'Rounding' | string;
  step: string;
  inputValue: RedactedWaterfallValue;
  operation: string;
  outputValue: RedactedWaterfallValue;
  configRef: string;
  reasonCode: string;
  roundingMode: string | null;
  inputDetails?: string[];
  outputDetails?: string[];
  marginRefs?: string[];
  adjustmentRefs?: string[];
}`,signature:{properties:[{key:`ordinal`,value:{name:`number`,required:!0}},{key:`section`,value:{name:`union`,raw:`'Base Rate' | 'Adjustments' | 'Margins' | 'Rounding' | string`,elements:[{name:`literal`,value:`'Base Rate'`},{name:`literal`,value:`'Adjustments'`},{name:`literal`,value:`'Margins'`},{name:`literal`,value:`'Rounding'`},{name:`string`}],required:!1}},{key:`step`,value:{name:`string`,required:!0}},{key:`inputValue`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`operation`,value:{name:`string`,required:!0}},{key:`outputValue`,value:{name:`signature`,type:`object`,raw:`{
  value: string | null;
  redacted: boolean;
  reason: string | null;
  auditRef?: string | null;
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`configRef`,value:{name:`string`,required:!0}},{key:`reasonCode`,value:{name:`string`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`inputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`outputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`WaterfallLedgerRow[]`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`roundingTraceRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`precision`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{ code: string; message: string; sourceRef: string; remediation?: string }`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!1}}]}}],raw:`Array<{ code: string; message: string; sourceRef: string; remediation?: string }>`,required:!0}},{key:`versionRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`auditRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`replayHash`,value:{name:`string`,required:!0}},{key:`versionGraphHash`,value:{name:`string`,required:!0}},{key:`resultHash`,value:{name:`string`,required:!0}},{key:`evidenceHash`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``},visualState:{required:!0,tsType:{name:`union`,raw:`'loading' | 'empty' | 'blocked' | 'needs-attention' | 'ready'`,elements:[{name:`literal`,value:`'loading'`},{name:`literal`,value:`'empty'`},{name:`literal`,value:`'blocked'`},{name:`literal`,value:`'needs-attention'`},{name:`literal`,value:`'ready'`}]},description:``},onNavigate:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(path: string) => void`,signature:{arguments:[{type:{name:`string`},name:`path`}],return:{name:`void`}}},description:``}}}}))();export{f as WaterfallLayout,d as default,C as exportWaterfallCsv,S as exportWaterfallJson,w as stateForWaterfall};