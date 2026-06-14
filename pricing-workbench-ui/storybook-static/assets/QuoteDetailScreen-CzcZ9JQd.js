import{i as e,s as t}from"./preload-helper-xPQekRTU.js";import{t as n}from"./iframe-CxjG8DRF.js";import{t as r}from"./jsx-runtime-CaZkqeYb.js";var i,a=e((()=>{i={tenantContext:`tenant-fixture`,runId:`run-preview-001`,offerId:`offer-a`,status:`READY`,summary:{offerId:`offer-a`,rank:1,productLabel:`Conventional 30 year fixed`,productFamily:`Conventional`,investor:`Investor A`,rate:`6.500%`,apr:`6.740%`,payment:`$2,104`,confidence:`96`,rankScore:`98`,lockPeriodDays:30,eligibilityStatus:`Eligible`,rationaleChips:[`Backend rank 1`,`Quote-service selected option`],scenarioFlags:[`CASH_OUT`],explanationStatus:`AVAILABLE`,explanationSections:[`Adjustment count: backend supplied 3`,`Margin summary: backend supplied branch and LO refs`],upstreamRefs:[`quote-service:offers`,`pricing-service:waterfall`,`adjustment-service:ledger`],lockEligibilityRefs:[`eligibility-service:lock-ready`],snapshotRefs:[`snapshot:offer-a`],auditIds:[`audit:offer-a`],sourceScenarioId:`scenario-fixture`,scenarioVersion:1},explanation:{runId:`run-preview-001`,offerId:`offer-a`,status:`AVAILABLE`,rationaleLines:[`Selected by backend rank service.`,`Pricing evidence is rendered from the returned waterfall ledger.`],scenarioFlags:[`CASH_OUT`],upstreamRefs:[`adjustment-service:adj-001`,`margin-service:margin-branch-001`,`eligibility-service:elig-001`],snapshotRefs:[`snapshot:offer-a`],auditIds:[`audit:offer-a`],explanationSections:[`Base grid selected by pricing-service.`,`Final price trace uses backend rounding refs.`],commitBlocked:!1,message:`Backend explanation available.`,uiTraceId:`pii-24-s11-explain-fixture`},waterfall:{tenantContext:`tenant-fixture`,runId:`run-preview-001`,status:`READY`,restrictedValuesVisible:!1,dependencyStatus:`fixture-backed`,baseSelection:{selectionId:`base-selection-001`,gridVersionRef:`grid:v2026-06-quote-fixture`,selectedNoteRate:{value:`6.500%`,redacted:!1,reason:null},basePrice:{value:`101.125`,redacted:!1,reason:null},ledgerSteps:[`base-grid`,`adjustments`,`margin`,`rounding`]},finalPrice:{finalPriceId:`final-price-001`,roundedFinalPrice:{value:`100.875`,redacted:!1,reason:null},adjustmentRefs:[`adjustment-service:adj-001`],roundingTraceRefs:[`pricing-service:rounding-trace-001`],ledger:[{ordinal:1,step:`Base grid`,inputValue:{value:`6.500%`,redacted:!1,reason:null},operation:`BACKEND_BASE_SELECTION`,outputValue:{value:`101.125`,redacted:!1,reason:null},configRef:`grid:v2026-06-quote-fixture`,reasonCode:`BASE_GRID`,roundingMode:null},{ordinal:2,step:`Compensation adjustment`,inputValue:{value:null,redacted:!0,reason:`COMPENSATION_CONFIDENTIAL`},operation:`BACKEND_ADJUSTMENT`,outputValue:{value:null,redacted:!0,reason:`COMPENSATION_CONFIDENTIAL`},configRef:`adjustment-service:adj-001`,reasonCode:`REDACTED_COMPENSATION`,roundingMode:null},{ordinal:3,step:`Final rounding`,inputValue:{value:`100.878`,redacted:!1,reason:null},operation:`BACKEND_ROUNDING`,outputValue:{value:`100.875`,redacted:!1,reason:null},configRef:`pricing-service:rounding-trace-001`,reasonCode:`ROUNDING_TRACE`,roundingMode:`nearest-eighth-from-backend`}]},blockers:[],versionRefs:[`pricing-config:v2026-06`,`margin-config:v2026-06`],auditRefs:[`audit:offer-a`,`audit:waterfall-001`],replayHash:`replay-hash-offer-a`,versionGraphHash:`version-graph-hash-offer-a`,resultHash:`result-hash-offer-a`,evidenceHash:`evidence-hash-offer-a`,uiTraceId:`pii-24-s11-waterfall-fixture`,events:[`fixture:quote-detail-waterfall-rendered`],fallbackReason:``},panels:[{panelId:`summary`,label:`Summary`,status:`READY`,fields:[`productLabel`,`investor`,`rate`,`apr`,`payment`,`rank`],backendRefs:[`quote-service:offers`],blockers:[]},{panelId:`waterfall`,label:`Waterfall`,status:`READY`,fields:[`baseSelection`,`ledger`,`finalPrice`],backendRefs:[`pricing-service:waterfall`],blockers:[]},{panelId:`compliance`,label:`Compliance`,status:`READY`,fields:[`complianceFlags`],backendRefs:[`compliance-service:evidence`],blockers:[]},{panelId:`audit`,label:`Audit / Replay`,status:`READY`,fields:[`auditRefs`,`replayHash`,`evidenceHash`],backendRefs:[`audit-replay-service:package`],blockers:[]}],redactions:[{fieldPath:`waterfall.finalPrice.ledger[1].inputValue`,state:`REDACTED`,reason:`COMPENSATION_CONFIDENTIAL`,auditRef:`audit:redaction-001`},{fieldPath:`waterfall.finalPrice.ledger[1].outputValue`,state:`REDACTED`,reason:`COMPENSATION_CONFIDENTIAL`,auditRef:`audit:redaction-001`}],complianceFlags:[`ATR_QM|PASS|Federal|/compliance/evidence/ATR_QM/audit:offer-a`,`HPML|REVIEW|State|/compliance/evidence/HPML/audit:offer-a`],auditRefs:[`audit:offer-a`,`audit:waterfall-001`],replayHash:`replay-hash-offer-a`,evidenceHash:`evidence-hash-offer-a`,uiTraceId:`pii-24-s11-local-fixture`,events:[`fixture:quote-detail-rendered`,`fixture:redaction-metadata-present`],fallbackReason:``}}));function o({tenantId:e=`tenant-fixture`,runId:t,optionId:n,uiTraceId:r=`pii-24-s11-local-trace`,detail:a=i,onEvidenceCapture:o,onNavigate:f}){let h=t??a.runId,g=n??a.offerId,[S,C]=(0,y.useState)(`summary`),[w,T]=(0,y.useState)(``),E=_(a);(0,y.useEffect)(()=>{o?.({screenId:`quote-detail`,timestamp:new Date().toISOString(),state:E,dataRefs:[e,h,g,a.uiTraceId,r,a.replayHash,a.evidenceHash],blockers:E===`blocked`?a.panels.flatMap(e=>e.blockers):[]})},[g,h,a,o,e,r,E]);let D=(0,y.useMemo)(()=>Object.fromEntries(a.panels.map(e=>[e.panelId,e.status])),[a.panels]);function O(e){f?.(e)}return(0,b.jsxs)(`main`,{className:`quote-detail-screen`,"aria-labelledby":`quote-detail-title`,children:[(0,b.jsxs)(`section`,{className:`hero`,"aria-labelledby":`quote-detail-title`,children:[(0,b.jsx)(`p`,{className:`eyebrow`,children:`Quote detail | PII-24-S11`}),(0,b.jsx)(`h1`,{id:`quote-detail-title`,children:`Quote Detail Waterfall`}),(0,b.jsx)(`p`,{children:`Backend evidence for one quote option. The UI renders returned values, references, hashes, and redaction metadata without pricing calculations.`}),(0,b.jsxs)(`div`,{className:`status-grid`,"aria-label":`Offer summary header`,children:[(0,b.jsx)(`dt`,{children:`Run`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:h})}),(0,b.jsx)(`dt`,{children:`Offer`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:g})}),(0,b.jsx)(`dt`,{children:`Product`}),(0,b.jsx)(`dd`,{children:v(a.summary.productLabel)}),(0,b.jsx)(`dt`,{children:`Rate / Price`}),(0,b.jsxs)(`dd`,{children:[v(a.summary.rate),` / `,p(a.waterfall.finalPrice.roundedFinalPrice,m(a.redactions,`waterfall.finalPrice.roundedFinalPrice`),O)]})]}),(0,b.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>O(`/quote/${encodeURIComponent(h)}/offers`),children:`Back to offers`})]}),E===`blocked`?(0,b.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:`Quote detail is blocked by backend evidence boundaries.`}):null,E===`empty`?(0,b.jsx)(`div`,{className:`banner banner--info`,children:`No quote detail data is available for this option.`}):null,(0,b.jsx)(`nav`,{className:`panel`,"aria-label":`Quote detail panel navigation`,children:(0,b.jsx)(`div`,{className:`ds-tab-list`,role:`tablist`,"aria-label":`Quote detail panels`,children:x.map(e=>(0,b.jsxs)(`button`,{type:`button`,role:`tab`,"aria-selected":S===e.id,"aria-controls":`${e.id}-panel`,onClick:()=>C(e.id),children:[e.label,` `,(0,b.jsx)(`span`,{className:`trace-badge`,children:D[e.id]??`READY`})]},e.id))})}),(0,b.jsxs)(`section`,{className:`quote-detail-layout`,"aria-label":`Responsive quote detail layout`,style:{display:`grid`,gap:`1rem`,gridTemplateColumns:`repeat(auto-fit, minmax(min(100%, 20rem), 1fr))`},children:[(0,b.jsxs)(`div`,{id:`summary-panel`,role:`tabpanel`,"aria-label":`Summary and explanation panel`,hidden:!1,children:[(0,b.jsx)(s,{detail:a,active:S===`summary`,onNavigate:O}),(0,b.jsx)(c,{detail:a})]}),(0,b.jsx)(`div`,{id:`waterfall-panel`,role:`tabpanel`,"aria-label":`Pricing waterfall panel`,hidden:!1,children:(0,b.jsx)(l,{waterfall:a.waterfall,redactions:a.redactions,exportText:w,onExport:T,onNavigate:O})}),(0,b.jsxs)(`div`,{id:`compliance-panel`,role:`tabpanel`,"aria-label":`Compliance and audit panel`,hidden:!1,children:[(0,b.jsx)(u,{flags:a.complianceFlags,onNavigate:O}),(0,b.jsx)(d,{detail:a,onNavigate:O})]})]})]})}function s({detail:e,onNavigate:t}){let{summary:n}=e;return(0,b.jsxs)(`section`,{className:`panel`,"aria-labelledby":`summary-heading`,children:[(0,b.jsx)(`h2`,{id:`summary-heading`,children:`Summary`}),(0,b.jsxs)(`dl`,{className:`status-grid`,children:[(0,b.jsx)(`dt`,{children:`Product`}),(0,b.jsx)(`dd`,{children:v(n.productLabel)}),(0,b.jsx)(`dt`,{children:`Investor`}),(0,b.jsx)(`dd`,{children:v(n.investor)}),(0,b.jsx)(`dt`,{children:`Channel`}),(0,b.jsx)(`dd`,{children:v(n.productFamily)}),(0,b.jsx)(`dt`,{children:`Lock period`}),(0,b.jsx)(`dd`,{children:v(n.lockPeriodDays)}),(0,b.jsx)(`dt`,{children:`Note rate`}),(0,b.jsx)(`dd`,{children:v(n.rate)}),(0,b.jsx)(`dt`,{children:`APR`}),(0,b.jsx)(`dd`,{children:v(n.apr)}),(0,b.jsx)(`dt`,{children:`Payment`}),(0,b.jsx)(`dd`,{children:v(n.payment)}),(0,b.jsx)(`dt`,{children:`Final price`}),(0,b.jsx)(`dd`,{children:p(e.waterfall.finalPrice.roundedFinalPrice,m(e.redactions,`waterfall.finalPrice.roundedFinalPrice`),t)}),(0,b.jsx)(`dt`,{children:`Confidence`}),(0,b.jsx)(`dd`,{children:v(n.confidence)}),(0,b.jsx)(`dt`,{children:`Rank / score`}),(0,b.jsxs)(`dd`,{children:[n.rank,` / `,v(n.rankScore)]}),(0,b.jsx)(`dt`,{children:`Source scenario`}),(0,b.jsxs)(`dd`,{children:[v(n.sourceScenarioId),` v`,v(n.scenarioVersion)]})]}),(0,b.jsx)(f,{label:`Scenario flags`,values:n.scenarioFlags}),(0,b.jsx)(f,{label:`Backend adjustment and margin summaries`,values:n.explanationSections??[]})]})}function c({detail:e}){return(0,b.jsxs)(`section`,{className:`panel`,"aria-labelledby":`explanation-heading`,children:[(0,b.jsx)(`h2`,{id:`explanation-heading`,children:`Explanation`}),(0,b.jsx)(`p`,{children:e.explanation.message}),(0,b.jsx)(`ul`,{children:e.explanation.rationaleLines.map(e=>(0,b.jsx)(`li`,{children:e},e))}),(0,b.jsx)(f,{label:`Upstream refs`,values:e.explanation.upstreamRefs??[]}),(0,b.jsx)(f,{label:`Snapshot refs`,values:e.explanation.snapshotRefs??[]}),(0,b.jsx)(f,{label:`Audit IDs`,values:e.explanation.auditIds??[]}),(0,b.jsx)(`button`,{type:`button`,onClick:()=>void navigator.clipboard?.writeText(e.explanation.rationaleLines.join(`
`)),children:`Copy Explanation`})]})}function l({waterfall:e,redactions:t,exportText:n,onExport:r,onNavigate:i}){return(0,b.jsxs)(`section`,{className:`panel`,"aria-labelledby":`waterfall-heading`,children:[(0,b.jsx)(`h2`,{id:`waterfall-heading`,children:`Pricing Waterfall`}),(0,b.jsxs)(`details`,{open:!0,children:[(0,b.jsx)(`summary`,{children:`Base selection`}),(0,b.jsxs)(`dl`,{className:`status-grid`,children:[(0,b.jsx)(`dt`,{children:`Grid version`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:e.baseSelection.gridVersionRef})}),(0,b.jsx)(`dt`,{children:`Selected note rate`}),(0,b.jsx)(`dd`,{children:p(e.baseSelection.selectedNoteRate,m(t,`waterfall.baseSelection.selectedNoteRate`),i)}),(0,b.jsx)(`dt`,{children:`Base price`}),(0,b.jsx)(`dd`,{children:p(e.baseSelection.basePrice,m(t,`waterfall.baseSelection.basePrice`),i)})]})]}),(0,b.jsxs)(`details`,{open:!0,children:[(0,b.jsx)(`summary`,{children:`Ledger rows`}),(0,b.jsxs)(`table`,{className:`ds-table`,"aria-label":`Pricing waterfall ledger`,children:[(0,b.jsx)(`thead`,{children:(0,b.jsxs)(`tr`,{children:[(0,b.jsx)(`th`,{scope:`col`,children:`#`}),(0,b.jsx)(`th`,{scope:`col`,children:`Step`}),(0,b.jsx)(`th`,{scope:`col`,children:`Input`}),(0,b.jsx)(`th`,{scope:`col`,children:`Operation`}),(0,b.jsx)(`th`,{scope:`col`,children:`Output`}),(0,b.jsx)(`th`,{scope:`col`,children:`Config ref`}),(0,b.jsx)(`th`,{scope:`col`,children:`Reason`}),(0,b.jsx)(`th`,{scope:`col`,children:`Rounding`})]})}),(0,b.jsx)(`tbody`,{children:e.finalPrice.ledger.map(e=>(0,b.jsxs)(`tr`,{children:[(0,b.jsx)(`td`,{children:e.ordinal}),(0,b.jsx)(`td`,{children:e.step}),(0,b.jsx)(`td`,{children:p(e.inputValue,m(t,`waterfall.finalPrice.ledger[${e.ordinal-1}].inputValue`),i)}),(0,b.jsx)(`td`,{children:e.operation}),(0,b.jsx)(`td`,{children:p(e.outputValue,m(t,`waterfall.finalPrice.ledger[${e.ordinal-1}].outputValue`),i)}),(0,b.jsx)(`td`,{children:(0,b.jsx)(`code`,{children:e.configRef})}),(0,b.jsx)(`td`,{children:e.reasonCode}),(0,b.jsx)(`td`,{children:v(e.roundingMode)})]},e.ordinal))})]})]}),(0,b.jsxs)(`details`,{open:!0,children:[(0,b.jsx)(`summary`,{children:`Final price and trace refs`}),(0,b.jsxs)(`p`,{children:[`Rounded final price: `,p(e.finalPrice.roundedFinalPrice,m(t,`waterfall.finalPrice.roundedFinalPrice`),i)]}),(0,b.jsx)(f,{label:`Adjustment refs`,values:e.finalPrice.adjustmentRefs}),(0,b.jsx)(f,{label:`Rounding trace refs`,values:e.finalPrice.roundingTraceRefs})]}),(0,b.jsx)(`button`,{type:`button`,onClick:()=>r(g(e,t)),children:`Export Waterfall`}),n?(0,b.jsx)(`textarea`,{"aria-label":`Exported waterfall data`,readOnly:!0,value:n}):null]})}function u({flags:e,onNavigate:t}){return(0,b.jsxs)(`section`,{className:`panel`,"aria-labelledby":`compliance-heading`,children:[(0,b.jsx)(`h2`,{id:`compliance-heading`,children:`Compliance`}),e.length===0?(0,b.jsx)(`p`,{children:`No compliance flags returned.`}):(0,b.jsx)(`ul`,{className:`offer-list`,children:e.map(e=>{let n=h(e);return(0,b.jsxs)(`li`,{children:[(0,b.jsx)(`strong`,{children:n.code}),` `,(0,b.jsx)(`span`,{className:`trace-badge`,children:n.severity}),(0,b.jsx)(`p`,{children:n.jurisdiction}),(0,b.jsx)(`button`,{type:`button`,onClick:()=>t(n.target),children:`Open compliance evidence`})]},e)})})]})}function d({detail:e,onNavigate:t}){return(0,b.jsxs)(`section`,{className:`panel`,"aria-labelledby":`audit-heading`,children:[(0,b.jsx)(`h2`,{id:`audit-heading`,children:`Audit / Replay`}),(0,b.jsxs)(`dl`,{className:`status-grid`,children:[(0,b.jsx)(`dt`,{children:`Replay hash`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>void navigator.clipboard?.writeText(e.replayHash),children:e.replayHash})}),(0,b.jsx)(`dt`,{children:`Evidence hash`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:e.evidenceHash})}),(0,b.jsx)(`dt`,{children:`Result hash`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:e.waterfall.resultHash})}),(0,b.jsx)(`dt`,{children:`Version graph hash`}),(0,b.jsx)(`dd`,{children:(0,b.jsx)(`code`,{children:e.waterfall.versionGraphHash})})]}),(0,b.jsx)(f,{label:`Version refs`,values:e.waterfall.versionRefs}),(0,b.jsx)(f,{label:`Audit refs`,values:e.auditRefs}),(0,b.jsx)(f,{label:`Events timeline`,values:e.events}),(0,b.jsx)(`button`,{type:`button`,onClick:()=>t(`/audit/replay?ref=${encodeURIComponent(e.replayHash)}`),children:`Open audit replay`})]})}function f({label:e,values:t}){return(0,b.jsxs)(`div`,{className:`copyable-ref-list`,"aria-label":e,children:[(0,b.jsx)(`strong`,{children:e}),t.length===0?(0,b.jsx)(`p`,{children:`N/A`}):(0,b.jsx)(`ul`,{children:t.map(e=>(0,b.jsx)(`li`,{children:(0,b.jsx)(`code`,{children:e})},e))})]})}function p(e,t,n){if(!e.redacted)return(0,b.jsx)(`span`,{children:v(e.value)});let r=t?.reason??e.reason??`REDACTED_BY_BACKEND`,i=t?.auditRef??`audit-ref-unavailable`;return(0,b.jsxs)(`span`,{title:`Reason: ${r}; audit ref: ${i}`,children:[(0,b.jsx)(`strong`,{children:`[REDACTED]`}),` `,(0,b.jsxs)(`small`,{children:[r,` | `,(0,b.jsx)(`code`,{children:i})]}),` `,(0,b.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>n(`/audit/replay?ref=${encodeURIComponent(i)}`),children:`Request Access`})]})}function m(e,t){return e.find(e=>e.fieldPath===t)}function h(e){let[t=e,n=`BACKEND_SUPPLIED`,r=`Evidence target from backend flag`,i=`/compliance/evidence/${encodeURIComponent(e)}`]=e.split(`|`);return{code:t,severity:n,jurisdiction:r,target:i}}function g(e,t){let n=e.finalPrice.ledger.map(e=>[e.ordinal,e.step,v(e.inputValue.value),e.inputValue.redacted?e.inputValue.reason:``,e.operation,v(e.outputValue.value),e.outputValue.redacted?e.outputValue.reason:``,e.configRef,e.reasonCode,v(e.roundingMode)].join(`,`));return[`# JSON`,JSON.stringify({waterfall:e,redactions:t},null,2),`# CSV`,`ordinal,step,input,redactedInputReason,operation,output,redactedOutputReason,configRef,reasonCode,roundingMode`,...n].join(`
`)}function _(e){return!e.summary||!e.waterfall?`empty`:e.status===`BLOCKED`||e.panels.some(e=>e.status===`BLOCKED`)?`blocked`:e.redactions.length>0||e.complianceFlags.length>0?`needs-attention`:`ready`}function v(e){return e==null||e===``?`N/A`:String(e)}var y,b,x;e((()=>{y=t(n(),1),a(),b=r(),x=[{id:`summary`,label:`Summary`},{id:`waterfall`,label:`Waterfall`},{id:`compliance`,label:`Compliance`},{id:`audit`,label:`Audit / Replay`}],o.__docgenInfo={description:``,methods:[],displayName:`QuoteDetailScreen`,props:{detail:{required:!1,tsType:{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  runId: string;
  offerId: string;
  status: string;
  summary: OfferSummary;
  explanation: OfferExplanationView;
  waterfall: PricingWaterfallView;
  panels: QuoteDetailPanel[];
  redactions: QuoteDetailRedaction[];
  complianceFlags: string[];
  auditRefs: string[];
  replayHash: string;
  evidenceHash: string;
  uiTraceId: string;
  events: string[];
  fallbackReason: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`runId`,value:{name:`string`,required:!0}},{key:`offerId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`signature`,type:`object`,raw:`{
  offerId: string;
  rank: number;
  productLabel?: string | null;
  productFamily?: string | null;
  investor?: string | null;
  rate?: string | number | null;
  payment?: string | number | null;
  apr?: string | number | null;
  confidence?: string | number | null;
  rankScore?: string | number | null;
  lockPeriodDays?: string | number | null;
  eligibilityStatus?: string | null;
  rationaleChips: string[];
  scenarioFlags: string[];
  explanationStatus: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  commitBlocked?: boolean;
  requiredFacts?: string[];
  sourceScenarioId?: string | null;
  scenarioVersion?: number | null;
  upstreamRefs?: string[];
  lockEligibilityRefs?: string[];
  snapshotRefs?: string[];
  auditIds?: string[];
  explanationSections?: string[];
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]},required:!0}},{key:`explanation`,value:{name:`signature`,type:`object`,raw:`{
  runId: string;
  offerId: string;
  status: 'AVAILABLE' | 'MISSING' | 'BLOCKED' | string;
  rationaleLines: string[];
  scenarioFlags: string[];
  upstreamRefs?: string[];
  snapshotRefs?: string[];
  auditIds?: string[];
  explanationSections?: string[];
  commitBlocked: boolean;
  message: string;
  uiTraceId: string;
}`,signature:{properties:[{key:`runId`,value:{name:`string`,required:!0}},{key:`offerId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`rationaleLines`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`commitBlocked`,value:{name:`boolean`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}}]},required:!0}},{key:`waterfall`,value:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`value`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`redacted`,value:{name:`boolean`,required:!0}},{key:`reason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`auditRef`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`configRef`,value:{name:`string`,required:!0}},{key:`reasonCode`,value:{name:`string`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`inputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`outputDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`WaterfallLedgerRow[]`,required:!0}},{key:`adjustmentRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`marginRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`roundingTraceRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`roundingMode`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`precision`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}}]},required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{ code: string; message: string; sourceRef: string; remediation?: string }`,signature:{properties:[{key:`code`,value:{name:`string`,required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`sourceRef`,value:{name:`string`,required:!0}},{key:`remediation`,value:{name:`string`,required:!1}}]}}],raw:`Array<{ code: string; message: string; sourceRef: string; remediation?: string }>`,required:!0}},{key:`versionRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`auditRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`replayHash`,value:{name:`string`,required:!0}},{key:`versionGraphHash`,value:{name:`string`,required:!0}},{key:`resultHash`,value:{name:`string`,required:!0}},{key:`evidenceHash`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]},required:!0}},{key:`panels`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  panelId: string;
  label: string;
  status: string;
  fields: string[];
  backendRefs: string[];
  blockers: string[];
}`,signature:{properties:[{key:`panelId`,value:{name:`string`,required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`fields`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`backendRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`blockers`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}}],raw:`QuoteDetailPanel[]`,required:!0}},{key:`redactions`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  fieldPath: string;
  state: string;
  reason: string;
  auditRef: string;
}`,signature:{properties:[{key:`fieldPath`,value:{name:`string`,required:!0}},{key:`state`,value:{name:`string`,required:!0}},{key:`reason`,value:{name:`string`,required:!0}},{key:`auditRef`,value:{name:`string`,required:!0}}]}}],raw:`QuoteDetailRedaction[]`,required:!0}},{key:`complianceFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`auditRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`replayHash`,value:{name:`string`,required:!0}},{key:`evidenceHash`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`fallbackReason`,value:{name:`string`,required:!0}}]}},description:``,defaultValue:{value:`{
  tenantContext: 'tenant-fixture',
  runId: 'run-preview-001',
  offerId: 'offer-a',
  status: 'READY',
  summary: {
    offerId: 'offer-a',
    rank: 1,
    productLabel: 'Conventional 30 year fixed',
    productFamily: 'Conventional',
    investor: 'Investor A',
    rate: '6.500%',
    apr: '6.740%',
    payment: '$2,104',
    confidence: '96',
    rankScore: '98',
    lockPeriodDays: 30,
    eligibilityStatus: 'Eligible',
    rationaleChips: ['Backend rank 1', 'Quote-service selected option'],
    scenarioFlags: ['CASH_OUT'],
    explanationStatus: 'AVAILABLE',
    explanationSections: ['Adjustment count: backend supplied 3', 'Margin summary: backend supplied branch and LO refs'],
    upstreamRefs: ['quote-service:offers', 'pricing-service:waterfall', 'adjustment-service:ledger'],
    lockEligibilityRefs: ['eligibility-service:lock-ready'],
    snapshotRefs: ['snapshot:offer-a'],
    auditIds: ['audit:offer-a'],
    sourceScenarioId: 'scenario-fixture',
    scenarioVersion: 1,
  },
  explanation: {
    runId: 'run-preview-001',
    offerId: 'offer-a',
    status: 'AVAILABLE',
    rationaleLines: ['Selected by backend rank service.', 'Pricing evidence is rendered from the returned waterfall ledger.'],
    scenarioFlags: ['CASH_OUT'],
    upstreamRefs: ['adjustment-service:adj-001', 'margin-service:margin-branch-001', 'eligibility-service:elig-001'],
    snapshotRefs: ['snapshot:offer-a'],
    auditIds: ['audit:offer-a'],
    explanationSections: ['Base grid selected by pricing-service.', 'Final price trace uses backend rounding refs.'],
    commitBlocked: false,
    message: 'Backend explanation available.',
    uiTraceId: 'pii-24-s11-explain-fixture',
  },
  waterfall: {
    tenantContext: 'tenant-fixture',
    runId: 'run-preview-001',
    status: 'READY',
    restrictedValuesVisible: false,
    dependencyStatus: 'fixture-backed',
    baseSelection: {
      selectionId: 'base-selection-001',
      gridVersionRef: 'grid:v2026-06-quote-fixture',
      selectedNoteRate: { value: '6.500%', redacted: false, reason: null },
      basePrice: { value: '101.125', redacted: false, reason: null },
      ledgerSteps: ['base-grid', 'adjustments', 'margin', 'rounding'],
    },
    finalPrice: {
      finalPriceId: 'final-price-001',
      roundedFinalPrice: { value: '100.875', redacted: false, reason: null },
      adjustmentRefs: ['adjustment-service:adj-001'],
      roundingTraceRefs: ['pricing-service:rounding-trace-001'],
      ledger: [
        {
          ordinal: 1,
          step: 'Base grid',
          inputValue: { value: '6.500%', redacted: false, reason: null },
          operation: 'BACKEND_BASE_SELECTION',
          outputValue: { value: '101.125', redacted: false, reason: null },
          configRef: 'grid:v2026-06-quote-fixture',
          reasonCode: 'BASE_GRID',
          roundingMode: null,
        },
        {
          ordinal: 2,
          step: 'Compensation adjustment',
          inputValue: { value: null, redacted: true, reason: 'COMPENSATION_CONFIDENTIAL' },
          operation: 'BACKEND_ADJUSTMENT',
          outputValue: { value: null, redacted: true, reason: 'COMPENSATION_CONFIDENTIAL' },
          configRef: 'adjustment-service:adj-001',
          reasonCode: 'REDACTED_COMPENSATION',
          roundingMode: null,
        },
        {
          ordinal: 3,
          step: 'Final rounding',
          inputValue: { value: '100.878', redacted: false, reason: null },
          operation: 'BACKEND_ROUNDING',
          outputValue: { value: '100.875', redacted: false, reason: null },
          configRef: 'pricing-service:rounding-trace-001',
          reasonCode: 'ROUNDING_TRACE',
          roundingMode: 'nearest-eighth-from-backend',
        },
      ],
    },
    blockers: [],
    versionRefs: ['pricing-config:v2026-06', 'margin-config:v2026-06'],
    auditRefs: ['audit:offer-a', 'audit:waterfall-001'],
    replayHash: 'replay-hash-offer-a',
    versionGraphHash: 'version-graph-hash-offer-a',
    resultHash: 'result-hash-offer-a',
    evidenceHash: 'evidence-hash-offer-a',
    uiTraceId: 'pii-24-s11-waterfall-fixture',
    events: ['fixture:quote-detail-waterfall-rendered'],
    fallbackReason: '',
  },
  panels: [
    { panelId: 'summary', label: 'Summary', status: 'READY', fields: ['productLabel', 'investor', 'rate', 'apr', 'payment', 'rank'], backendRefs: ['quote-service:offers'], blockers: [] },
    { panelId: 'waterfall', label: 'Waterfall', status: 'READY', fields: ['baseSelection', 'ledger', 'finalPrice'], backendRefs: ['pricing-service:waterfall'], blockers: [] },
    { panelId: 'compliance', label: 'Compliance', status: 'READY', fields: ['complianceFlags'], backendRefs: ['compliance-service:evidence'], blockers: [] },
    { panelId: 'audit', label: 'Audit / Replay', status: 'READY', fields: ['auditRefs', 'replayHash', 'evidenceHash'], backendRefs: ['audit-replay-service:package'], blockers: [] },
  ],
  redactions: [
    { fieldPath: 'waterfall.finalPrice.ledger[1].inputValue', state: 'REDACTED', reason: 'COMPENSATION_CONFIDENTIAL', auditRef: 'audit:redaction-001' },
    { fieldPath: 'waterfall.finalPrice.ledger[1].outputValue', state: 'REDACTED', reason: 'COMPENSATION_CONFIDENTIAL', auditRef: 'audit:redaction-001' },
  ],
  complianceFlags: ['ATR_QM|PASS|Federal|/compliance/evidence/ATR_QM/audit:offer-a', 'HPML|REVIEW|State|/compliance/evidence/HPML/audit:offer-a'],
  auditRefs: ['audit:offer-a', 'audit:waterfall-001'],
  replayHash: 'replay-hash-offer-a',
  evidenceHash: 'evidence-hash-offer-a',
  uiTraceId: 'pii-24-s11-local-fixture',
  events: ['fixture:quote-detail-rendered', 'fixture:redaction-metadata-present'],
  fallbackReason: '',
}`,computed:!1}},onNavigate:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(path: string) => void`,signature:{arguments:[{type:{name:`string`},name:`path`}],return:{name:`void`}}},description:``},tenantId:{defaultValue:{value:`'tenant-fixture'`,computed:!1},required:!1},uiTraceId:{defaultValue:{value:`'pii-24-s11-local-trace'`,computed:!1},required:!1}}}}))();export{o as default};