import{i as e,s as t}from"./preload-helper-xPQekRTU.js";import{t as n}from"./iframe-CxjG8DRF.js";import{t as r}from"./jsx-runtime-CaZkqeYb.js";function i({label:e,values:t}){return t.length?(0,a.jsx)(`ul`,{className:`chip-list`,"aria-label":e,children:t.map(e=>(0,a.jsx)(`li`,{children:e},e))}):(0,a.jsxs)(`p`,{className:`field-help`,children:[`No `,e.toLowerCase(),` provided.`]})}var a,o=e((()=>{n(),a=r(),i.__docgenInfo={description:``,methods:[],displayName:`ChipList`,props:{label:{required:!0,tsType:{name:`string`},description:``},values:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``}}}}));function s({reason:e,requiredFacts:t=[],backendRefs:n=[]}){return(0,c.jsxs)(`section`,{className:`panel`,"aria-labelledby":`blocked-offers-heading`,children:[(0,c.jsx)(`h2`,{id:`blocked-offers-heading`,children:`Offer comparison needs connected facts`}),(0,c.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:e??`The offer comparison response is blocked by missing upstream facts.`}),(0,c.jsx)(i,{label:`Required facts`,values:t}),(0,c.jsx)(i,{label:`Backend references`,values:n}),(0,c.jsx)(`a`,{href:`/pipeline`,children:`Return to Intake`})]})}var c,l=e((()=>{o(),c=r(),s.__docgenInfo={description:``,methods:[],displayName:`BlockedOffers`,props:{reason:{required:!1,tsType:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}]},description:``},requiredFacts:{required:!1,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``,defaultValue:{value:`[]`,computed:!1}},backendRefs:{required:!1,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``,defaultValue:{value:`[]`,computed:!1}}}}}));function u({fallbackReason:e}){return(0,d.jsxs)(`section`,{className:`panel`,"aria-labelledby":`empty-offers-heading`,children:[(0,d.jsx)(`h2`,{id:`empty-offers-heading`,children:`No offers available`}),(0,d.jsx)(`p`,{children:e??`No ranked offers were returned for this quote run.`}),(0,d.jsxs)(`ul`,{children:[(0,d.jsx)(`li`,{children:(0,d.jsx)(`a`,{href:`/pipeline`,children:`Adjust intake`})}),(0,d.jsx)(`li`,{children:(0,d.jsx)(`a`,{href:`/pipeline`,children:`Check eligibility inputs`})}),(0,d.jsx)(`li`,{children:(0,d.jsx)(`a`,{href:`/quality/validation`,children:`Contact pricing admin`})})]})]})}var d,f=e((()=>{d=r(),u.__docgenInfo={description:``,methods:[],displayName:`EmptyOffers`,props:{fallbackReason:{required:!1,tsType:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}]},description:``}}}}));function p({offer:e,onViewFull:t}){if(!e)return(0,m.jsxs)(`aside`,{className:`panel`,"aria-labelledby":`explanation-preview-heading`,children:[(0,m.jsx)(`h2`,{id:`explanation-preview-heading`,children:`Explanation preview`}),(0,m.jsx)(`p`,{children:`Choose an offer to preview rationale, scenario flags, and upstream references.`})]});let n=e.explanationStatus!==`AVAILABLE`;return(0,m.jsxs)(`aside`,{className:`panel`,"aria-labelledby":`explanation-preview-heading`,children:[(0,m.jsx)(`h2`,{id:`explanation-preview-heading`,children:`Explanation preview`}),n?(0,m.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:`Explanation unavailable from the connected boundary.`}):null,(0,m.jsx)(i,{label:`Rationale lines`,values:e.rationaleChips}),(0,m.jsx)(i,{label:`Scenario flags`,values:e.scenarioFlags}),(0,m.jsx)(i,{label:`Upstream references`,values:e.upstreamRefs??[]}),(0,m.jsx)(i,{label:`Snapshot references`,values:e.snapshotRefs??[]}),(0,m.jsx)(`button`,{type:`button`,onClick:()=>t(e.offerId),children:`View Full Explanation`})]})}var m,h=e((()=>{o(),m=r(),p.__docgenInfo={description:``,methods:[],displayName:`ExplanationPreview`,props:{offer:{required:!0,tsType:{name:`union`,raw:`OfferSummary | null`,elements:[{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},{name:`null`}]},description:``},onViewFull:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offerId: string) => void`,signature:{arguments:[{type:{name:`string`},name:`offerId`}],return:{name:`void`}}},description:``}}}})),g,_=e((()=>{g={runId:`run-preview-001`,status:`READY_WITH_FIXTURE_DATA`,selectedOfferId:null,commitBlocked:!1,sortOptions:[`rank`,`rate`,`apr`,`payment`,`confidence`,`rankScore`],fallbackReason:null,requiredFacts:[],backendRefs:[`quote-service:fixture-unavailable`,`pricing-service:fixture-unavailable`],uiTraceId:`pii-24-s10-local-fixture`,events:[`fixture:offer-comparison-rendered`],offers:[{offerId:`offer-a`,rank:1,productLabel:`Conventional 30 year fixed`,productFamily:`Conventional`,investor:`Investor A`,rate:6.5,apr:6.74,payment:2104,confidence:96,rankScore:98,lockPeriodDays:30,eligibilityStatus:`Eligible`,rationaleChips:[`Backend rank 1`,`PRICE_UNAVAILABLE guarded fallback`],scenarioFlags:[`CASH_OUT`],explanationStatus:`AVAILABLE`,explanationSections:[`Pricing summary`,`Eligibility refs`],upstreamRefs:[`quote-service:offers`],lockEligibilityRefs:[`eligibility-service:lock-ready`],snapshotRefs:[`snapshot:offer-a`],auditIds:[`audit:offer-a`],sourceScenarioId:`scenario-fixture`,scenarioVersion:1},{offerId:`offer-b`,rank:2,productLabel:`FHA fixed`,productFamily:`FHA`,investor:`Investor B`,rate:null,apr:null,payment:null,confidence:72,rankScore:81,lockPeriodDays:45,eligibilityStatus:`Needs facts`,rationaleChips:[`PRICE_UNAVAILABLE`,`Backend requires missing facts`],scenarioFlags:[`MI_LTV_80`],explanationStatus:`BLOCKED`,commitBlocked:!0,requiredFacts:[`pricing-service quote price`,`eligibility lock facts`],upstreamRefs:[`pricing-service:missing-price`],lockEligibilityRefs:[],snapshotRefs:[`snapshot:offer-b`],auditIds:[`audit:offer-b`],sourceScenarioId:`scenario-fixture`,scenarioVersion:1}]}}));function v(e,t){return e.map((e,t)=>({offer:e,index:t})).sort((e,n)=>{let r=w(e.offer,n.offer,t.field);return(t.direction===`asc`?r:-r)||e.offer.rank-n.offer.rank||e.index-n.index}).map(({offer:e})=>e)}function y(e,t){let n=E(t.rateMax),r=E(t.confidenceMin);return e.filter(e=>{if(t.productFamily&&O(e.productFamily??e.productLabel)!==O(t.productFamily)||t.investor&&O(e.investor)!==O(t.investor)||t.lockPeriodDays&&O(e.lockPeriodDays)!==O(t.lockPeriodDays)||t.eligibilityStatus&&O(e.eligibilityStatus)!==O(t.eligibilityStatus))return!1;let i=D(e.rate);if(n!==null&&i!==null&&i>n)return!1;let a=D(e.confidence);return!(r!==null&&a!==null&&a<r)})}function b(e,t){return Array.from(new Set(e.map(e=>C(t(e))).filter(e=>e!==`N/A`))).sort()}function x(e,t,n=4){return e.includes(t)?e.filter(e=>e!==t):e.length>=n?e:[...e,t]}function S(e){return e.commitBlocked&&e.offers.length===0?`blocked`:e.offers.length===0?`empty`:`ready`}function C(e){return e==null||e===``?`N/A`:String(e)}function w(e,t,n){let r=T(e,n),i=T(t,n);return r<i?-1:+(r>i)}function T(e,t){let n=t===`rank`?e.rank:e[t];return D(n)??C(n).toLowerCase()}function E(e){if(!e.trim())return null;let t=Number(e);return Number.isFinite(t)?t:null}function D(e){if(typeof e==`number`)return Number.isFinite(e)?e:null;if(typeof e!=`string`)return null;let t=Number(e.replace(/[%,$]/g,``));return Number.isFinite(t)?t:null}function O(e){return C(e).toLowerCase()}var k,A,j=e((()=>{k={productFamily:``,investor:``,rateMax:``,confidenceMin:``,lockPeriodDays:``,eligibilityStatus:``},A={field:`rank`,direction:`asc`}}));function M({offer:e,selected:t,compared:n,onInspect:r,onSelect:a,onCompareToggle:o}){return(0,N.jsxs)(`article`,{className:t?`offer-card offer-card--selected`:`offer-card`,"aria-label":`Offer ${e.offerId} rank ${e.rank}`,children:[(0,N.jsxs)(`div`,{className:`panel-heading-row`,children:[(0,N.jsxs)(`div`,{children:[(0,N.jsxs)(`p`,{className:`eyebrow`,children:[`Rank #`,e.rank]}),(0,N.jsx)(`h3`,{children:e.productLabel??e.offerId})]}),(0,N.jsxs)(`span`,{children:[C(e.confidence),` confidence`]})]}),(0,N.jsxs)(`dl`,{className:`status-grid`,children:[(0,N.jsx)(`dt`,{children:`Rate`}),(0,N.jsx)(`dd`,{children:C(e.rate)}),(0,N.jsx)(`dt`,{children:`APR`}),(0,N.jsx)(`dd`,{children:C(e.apr)}),(0,N.jsx)(`dt`,{children:`Payment`}),(0,N.jsx)(`dd`,{children:C(e.payment)}),(0,N.jsx)(`dt`,{children:`Rank score`}),(0,N.jsx)(`dd`,{children:C(e.rankScore)}),(0,N.jsx)(`dt`,{children:`Investor`}),(0,N.jsx)(`dd`,{children:C(e.investor)}),(0,N.jsx)(`dt`,{children:`Eligibility`}),(0,N.jsx)(`dd`,{children:C(e.eligibilityStatus)})]}),(0,N.jsx)(i,{label:`${e.offerId} rationale`,values:e.rationaleChips}),(0,N.jsx)(i,{label:`${e.offerId} flags`,values:e.scenarioFlags}),(0,N.jsxs)(`div`,{className:`quick-quote-state`,children:[(0,N.jsx)(`button`,{type:`button`,"aria-pressed":t,onClick:()=>a(e),children:`Select offer`}),(0,N.jsx)(`button`,{type:`button`,"aria-pressed":n,onClick:()=>o(e.offerId),children:`Compare`}),(0,N.jsx)(`button`,{type:`button`,onClick:()=>r(e),children:`Preview explanation`})]})]})}var N,P=e((()=>{o(),j(),N=r(),M.__docgenInfo={description:``,methods:[],displayName:`OfferCard`,props:{offer:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},description:``},selected:{required:!0,tsType:{name:`boolean`},description:``},compared:{required:!0,tsType:{name:`boolean`},description:``},onInspect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offer: OfferSummary) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},name:`offer`}],return:{name:`void`}}},description:``},onSelect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offer: OfferSummary) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},name:`offer`}],return:{name:`void`}}},description:``},onCompareToggle:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offerId: string) => void`,signature:{arguments:[{type:{name:`string`},name:`offerId`}],return:{name:`void`}}},description:``}}}}));function F({offers:e,selectedOfferId:t,compareOfferIds:n,onInspect:r,onSelect:a,onCompareToggle:o,onSortField:s}){let c=e.slice(0,z);return(0,R.jsxs)(`div`,{className:`quote-table`,role:`table`,"aria-label":`Ranked quote offers`,"aria-rowcount":e.length,children:[(0,R.jsxs)(`div`,{role:`row`,className:`quote-table__row quote-table__row--head`,children:[(0,R.jsx)(I,{label:`Rank`,field:`rank`,onSortField:s}),(0,R.jsx)(`span`,{role:`columnheader`,children:`Product`}),(0,R.jsx)(I,{label:`Rate`,field:`rate`,onSortField:s}),(0,R.jsx)(I,{label:`APR`,field:`apr`,onSortField:s}),(0,R.jsx)(I,{label:`Payment`,field:`payment`,onSortField:s}),(0,R.jsx)(I,{label:`Confidence`,field:`confidence`,onSortField:s}),(0,R.jsx)(I,{label:`Rank score`,field:`rankScore`,onSortField:s}),(0,R.jsx)(`span`,{role:`columnheader`,children:`Rationale`}),(0,R.jsx)(`span`,{role:`columnheader`,children:`Flags`}),(0,R.jsx)(`span`,{role:`columnheader`,children:`Actions`})]}),c.map(e=>{let s=t===e.offerId;return(0,R.jsxs)(`div`,{role:`row`,className:s?`quote-table__row quote-table__row--selected`:`quote-table__row`,"aria-selected":s,tabIndex:0,onKeyDown:t=>L(t,e,a,o),children:[(0,R.jsxs)(`span`,{role:`cell`,children:[`#`,e.rank]}),(0,R.jsx)(`span`,{role:`cell`,children:e.productLabel??e.offerId}),(0,R.jsx)(`span`,{role:`cell`,children:C(e.rate)}),(0,R.jsx)(`span`,{role:`cell`,children:C(e.apr)}),(0,R.jsx)(`span`,{role:`cell`,children:C(e.payment)}),(0,R.jsx)(`span`,{role:`cell`,children:C(e.confidence)}),(0,R.jsx)(`span`,{role:`cell`,children:C(e.rankScore)}),(0,R.jsx)(`span`,{role:`cell`,children:(0,R.jsx)(i,{label:`${e.offerId} rationale`,values:e.rationaleChips})}),(0,R.jsx)(`span`,{role:`cell`,children:(0,R.jsx)(i,{label:`${e.offerId} flags`,values:e.scenarioFlags})}),(0,R.jsxs)(`span`,{role:`cell`,className:`quick-quote-state`,children:[(0,R.jsxs)(`label`,{children:[(0,R.jsx)(`input`,{type:`radio`,name:`selected-offer`,checked:s,onChange:()=>a(e)}),` Select`]}),(0,R.jsxs)(`label`,{children:[(0,R.jsx)(`input`,{type:`checkbox`,checked:n.includes(e.offerId),onChange:()=>o(e.offerId)}),` Compare`]}),(0,R.jsx)(`button`,{type:`button`,onMouseEnter:()=>r(e),onFocus:()=>r(e),onClick:()=>r(e),children:`Preview`})]})]},e.offerId)}),e.length>z?(0,R.jsxs)(`p`,{role:`status`,children:[`Showing first `,z,` offers from a `,e.length,` offer result set.`]}):null]})}function I({label:e,field:t,onSortField:n}){return(0,R.jsx)(`button`,{type:`button`,role:`columnheader`,onClick:e=>n(t,e.shiftKey),children:e})}function L(e,t,n,r){e.key===`Enter`&&(e.preventDefault(),n(t)),e.key===` `&&(e.preventDefault(),r(t.offerId))}var R,z,B=e((()=>{o(),j(),R=r(),z=100,F.__docgenInfo={description:``,methods:[],displayName:`OffersTable`,props:{offers:{required:!0,tsType:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`OfferSummary[]`},description:``},selectedOfferId:{required:!0,tsType:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}]},description:``},compareOfferIds:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},onInspect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offer: OfferSummary) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},name:`offer`}],return:{name:`void`}}},description:``},onSelect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offer: OfferSummary) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},name:`offer`}],return:{name:`void`}}},description:``},onCompareToggle:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offerId: string) => void`,signature:{arguments:[{type:{name:`string`},name:`offerId`}],return:{name:`void`}}},description:``},onSortField:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(field: OfferSortField, additive: boolean) => void`,signature:{arguments:[{type:{name:`union`,raw:`'rank' | 'rate' | 'apr' | 'payment' | 'confidence' | 'rankScore'`,elements:[{name:`literal`,value:`'rank'`},{name:`literal`,value:`'rate'`},{name:`literal`,value:`'apr'`},{name:`literal`,value:`'payment'`},{name:`literal`,value:`'confidence'`},{name:`literal`,value:`'rankScore'`}]},name:`field`},{type:{name:`boolean`},name:`additive`}],return:{name:`void`}}},description:``}}}}));function V({sort:e,filters:t,productFamilies:n,investors:r,lockPeriods:i,eligibilityStates:a,viewMode:o,activeFilterCount:s,onSortChange:c,onFiltersChange:l,onViewModeChange:u,onReset:d}){return(0,H.jsxs)(`div`,{className:`offer-toolbar`,"aria-label":`Offer sort and filter controls`,children:[(0,H.jsxs)(`label`,{children:[`Sort`,(0,H.jsx)(`select`,{value:e.field,onChange:t=>c({...e,field:t.target.value}),children:U.map(e=>(0,H.jsx)(`option`,{value:e,children:e},e))})]}),(0,H.jsxs)(`label`,{children:[`Direction`,(0,H.jsxs)(`select`,{value:e.direction,onChange:t=>c({...e,direction:t.target.value}),children:[(0,H.jsx)(`option`,{value:`asc`,children:`Ascending`}),(0,H.jsx)(`option`,{value:`desc`,children:`Descending`})]})]}),(0,H.jsxs)(`label`,{children:[`Product family`,(0,H.jsxs)(`select`,{value:t.productFamily,onChange:e=>l({...t,productFamily:e.target.value}),children:[(0,H.jsx)(`option`,{value:``,children:`Any`}),n.map(e=>(0,H.jsx)(`option`,{value:e,children:e},e))]})]}),(0,H.jsxs)(`label`,{children:[`Investor`,(0,H.jsxs)(`select`,{value:t.investor,onChange:e=>l({...t,investor:e.target.value}),children:[(0,H.jsx)(`option`,{value:``,children:`Any`}),r.map(e=>(0,H.jsx)(`option`,{value:e,children:e},e))]})]}),(0,H.jsxs)(`label`,{children:[`Max rate`,(0,H.jsx)(`input`,{inputMode:`decimal`,value:t.rateMax,onChange:e=>l({...t,rateMax:e.target.value}),placeholder:`No max`})]}),(0,H.jsxs)(`label`,{children:[`Min confidence`,(0,H.jsx)(`input`,{inputMode:`numeric`,value:t.confidenceMin,onChange:e=>l({...t,confidenceMin:e.target.value}),placeholder:`No min`})]}),(0,H.jsxs)(`label`,{children:[`Lock period`,(0,H.jsxs)(`select`,{value:t.lockPeriodDays,onChange:e=>l({...t,lockPeriodDays:e.target.value}),children:[(0,H.jsx)(`option`,{value:``,children:`Any`}),i.map(e=>(0,H.jsx)(`option`,{value:e,children:e},e))]})]}),(0,H.jsxs)(`label`,{children:[`Eligibility`,(0,H.jsxs)(`select`,{value:t.eligibilityStatus,onChange:e=>l({...t,eligibilityStatus:e.target.value}),children:[(0,H.jsx)(`option`,{value:``,children:`Any`}),a.map(e=>(0,H.jsx)(`option`,{value:e,children:e},e))]})]}),(0,H.jsxs)(`div`,{className:`quick-quote-state`,"aria-label":`View mode`,children:[(0,H.jsx)(`button`,{type:`button`,"aria-pressed":o===`table`,onClick:()=>u(`table`),children:`Table`}),(0,H.jsx)(`button`,{type:`button`,"aria-pressed":o===`cards`,onClick:()=>u(`cards`),children:`Cards`})]}),(0,H.jsxs)(`button`,{type:`button`,onClick:d,children:[`Reset filters (`,s,`)`]})]})}var H,U,W=e((()=>{H=r(),U=[`rank`,`rate`,`apr`,`payment`,`confidence`,`rankScore`],V.__docgenInfo={description:``,methods:[],displayName:`OffersToolbar`,props:{sort:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  field: OfferSortField;
  direction: SortDirection;
}`,signature:{properties:[{key:`field`,value:{name:`union`,raw:`'rank' | 'rate' | 'apr' | 'payment' | 'confidence' | 'rankScore'`,elements:[{name:`literal`,value:`'rank'`},{name:`literal`,value:`'rate'`},{name:`literal`,value:`'apr'`},{name:`literal`,value:`'payment'`},{name:`literal`,value:`'confidence'`},{name:`literal`,value:`'rankScore'`}],required:!0}},{key:`direction`,value:{name:`union`,raw:`'asc' | 'desc'`,elements:[{name:`literal`,value:`'asc'`},{name:`literal`,value:`'desc'`}],required:!0}}]}},description:``},filters:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  productFamily: string;
  investor: string;
  rateMax: string;
  confidenceMin: string;
  lockPeriodDays: string;
  eligibilityStatus: string;
}`,signature:{properties:[{key:`productFamily`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`rateMax`,value:{name:`string`,required:!0}},{key:`confidenceMin`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`string`,required:!0}},{key:`eligibilityStatus`,value:{name:`string`,required:!0}}]}},description:``},productFamilies:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},investors:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},lockPeriods:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},eligibilityStates:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},viewMode:{required:!0,tsType:{name:`union`,raw:`'table' | 'cards'`,elements:[{name:`literal`,value:`'table'`},{name:`literal`,value:`'cards'`}]},description:``},activeFilterCount:{required:!0,tsType:{name:`number`},description:``},onSortChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(sort: OfferSort) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  field: OfferSortField;
  direction: SortDirection;
}`,signature:{properties:[{key:`field`,value:{name:`union`,raw:`'rank' | 'rate' | 'apr' | 'payment' | 'confidence' | 'rankScore'`,elements:[{name:`literal`,value:`'rank'`},{name:`literal`,value:`'rate'`},{name:`literal`,value:`'apr'`},{name:`literal`,value:`'payment'`},{name:`literal`,value:`'confidence'`},{name:`literal`,value:`'rankScore'`}],required:!0}},{key:`direction`,value:{name:`union`,raw:`'asc' | 'desc'`,elements:[{name:`literal`,value:`'asc'`},{name:`literal`,value:`'desc'`}],required:!0}}]}},name:`sort`}],return:{name:`void`}}},description:``},onFiltersChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(filters: OfferFilters) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  productFamily: string;
  investor: string;
  rateMax: string;
  confidenceMin: string;
  lockPeriodDays: string;
  eligibilityStatus: string;
}`,signature:{properties:[{key:`productFamily`,value:{name:`string`,required:!0}},{key:`investor`,value:{name:`string`,required:!0}},{key:`rateMax`,value:{name:`string`,required:!0}},{key:`confidenceMin`,value:{name:`string`,required:!0}},{key:`lockPeriodDays`,value:{name:`string`,required:!0}},{key:`eligibilityStatus`,value:{name:`string`,required:!0}}]}},name:`filters`}],return:{name:`void`}}},description:``},onViewModeChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(viewMode: 'table' | 'cards') => void`,signature:{arguments:[{type:{name:`union`,raw:`'table' | 'cards'`,elements:[{name:`literal`,value:`'table'`},{name:`literal`,value:`'cards'`}]},name:`viewMode`}],return:{name:`void`}}},description:``},onReset:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function G({runId:e,selectedOffer:t,comparison:n,compareOfferIds:r,onNavigateDetail:a,onNavigateLock:o}){if(!t)return null;let s=n.commitBlocked||t.commitBlocked,c=t.requiredFacts?.length?t.requiredFacts:n.requiredFacts??[];return(0,K.jsxs)(`section`,{className:`selection-bar`,"aria-label":`Selected offer actions`,children:[(0,K.jsxs)(`div`,{children:[(0,K.jsx)(`strong`,{children:t.productLabel??t.offerId}),(0,K.jsxs)(`span`,{children:[`Run `,e,` | rate `,C(t.rate),` | APR `,C(t.apr),` | compare `,r.length,`/4`]})]}),s?(0,K.jsxs)(`div`,{className:`banner banner--blocked`,role:`alert`,children:[(0,K.jsx)(`strong`,{children:`Commit blocked`}),(0,K.jsx)(i,{label:`Required facts`,values:c})]}):null,(0,K.jsxs)(`div`,{className:`quick-quote-state`,children:[(0,K.jsx)(`button`,{type:`button`,onClick:()=>a(t.offerId),children:`Compare Detail`}),(0,K.jsx)(`button`,{type:`button`,disabled:s,onClick:o,children:`Lock Terms`})]})]})}var K,q=e((()=>{o(),j(),K=r(),G.__docgenInfo={description:``,methods:[],displayName:`SelectionBar`,props:{runId:{required:!0,tsType:{name:`string`},description:``},selectedOffer:{required:!0,tsType:{name:`union`,raw:`OfferSummary | null`,elements:[{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}},{name:`null`}]},description:``},comparison:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  runId: string;
  status: string;
  offers: OfferSummary[];
  sortOptions: string[];
  selectedOfferId: string | null;
  commitBlocked: boolean;
  fallbackReason?: string | null;
  requiredFacts?: string[];
  backendRefs?: string[];
  uiTraceId: string;
  events: string[];
}`,signature:{properties:[{key:`runId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`offers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`OfferSummary[]`,required:!0}},{key:`sortOptions`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`selectedOfferId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!0}},{key:`fallbackReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`backendRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},description:``},compareOfferIds:{required:!0,tsType:{name:`Array`,elements:[{name:`string`}],raw:`string[]`},description:``},onNavigateDetail:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(offerId: string) => void`,signature:{arguments:[{type:{name:`string`},name:`offerId`}],return:{name:`void`}}},description:``},onNavigateLock:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function J({tenantId:e=`tenant-fixture`,runId:t,uiTraceId:n=`pii-24-s10-local-trace`,comparison:r=g,onEvidenceCapture:i,onNavigate:a}){let o=t??r.runId,[c,l]=(0,Y.useState)(A),[d,f]=(0,Y.useState)(k),[m,h]=(0,Y.useState)(r.selectedOfferId),[_,C]=(0,Y.useState)([]),[w,T]=(0,Y.useState)(r.offers[0]??null),[E,D]=(0,Y.useState)(`table`),O=(0,Y.useMemo)(()=>v(y(r.offers,d),c),[r.offers,d,c]),j=r.offers.find(e=>e.offerId===m)??null,N=S(r),P=Object.values(d).filter(Boolean).length;(0,Y.useEffect)(()=>{i?.({screenId:`quote-offers`,timestamp:new Date().toISOString(),state:N,dataRefs:[e,o,r.uiTraceId,n],blockers:N===`blocked`?r.requiredFacts??[]:[]})},[o,r.requiredFacts,r.uiTraceId,i,e,n,N]);function I(e){h(e.offerId),T(e)}function L(e){a?.(e)}function R(e,t){l(n=>({field:e,direction:n.field===e&&!t&&n.direction===`asc`?`desc`:`asc`}))}return(0,X.jsxs)(`main`,{className:`quote-offers-screen`,"aria-labelledby":`quote-offers-title`,children:[(0,X.jsxs)(`section`,{className:`hero`,"aria-labelledby":`quote-offers-title`,children:[(0,X.jsx)(`p`,{className:`eyebrow`,children:`Offer comparison | PII-24-S10`}),(0,X.jsx)(`h1`,{id:`quote-offers-title`,children:`Compare Offers`}),(0,X.jsx)(`p`,{children:`Review backend-ranked offers without calculating rates, eligibility, or investor decisions in the UI.`})]}),N===`empty`?(0,X.jsx)(u,{fallbackReason:r.fallbackReason}):null,N===`blocked`?(0,X.jsx)(s,{reason:r.fallbackReason,requiredFacts:r.requiredFacts,backendRefs:r.backendRefs}):null,N===`ready`?(0,X.jsxs)(X.Fragment,{children:[(0,X.jsx)(V,{sort:c,filters:d,productFamilies:b(r.offers,e=>e.productFamily??e.productLabel),investors:b(r.offers,e=>e.investor),lockPeriods:b(r.offers,e=>e.lockPeriodDays),eligibilityStates:b(r.offers,e=>e.eligibilityStatus),viewMode:E,activeFilterCount:P,onSortChange:l,onFiltersChange:f,onViewModeChange:D,onReset:()=>f(k)}),O.length===0?(0,X.jsx)(u,{fallbackReason:`No offers match the active filters.`}):E===`table`?(0,X.jsx)(F,{offers:O,selectedOfferId:m,compareOfferIds:_,onInspect:T,onSelect:I,onCompareToggle:e=>C(t=>x(t,e)),onSortField:R}):(0,X.jsx)(`div`,{className:`offer-grid`,role:`list`,"aria-label":`Offer cards`,children:O.map(e=>(0,X.jsx)(M,{offer:e,selected:m===e.offerId,compared:_.includes(e.offerId),onInspect:T,onSelect:I,onCompareToggle:e=>C(t=>x(t,e))},e.offerId))}),(0,X.jsx)(p,{offer:w,onViewFull:e=>L(`/quote/${encodeURIComponent(o)}/offers/${encodeURIComponent(e)}`)}),(0,X.jsx)(G,{runId:o,selectedOffer:j,comparison:r,compareOfferIds:_,onNavigateDetail:e=>L(`/quote/${encodeURIComponent(o)}/offers/${encodeURIComponent(e)}`),onNavigateLock:()=>L(`/quote/${encodeURIComponent(o)}/lock`)})]}):null]})}var Y,X;e((()=>{Y=t(n(),1),l(),f(),h(),_(),P(),j(),B(),W(),q(),X=r(),J.__docgenInfo={description:``,methods:[],displayName:`QuoteOffersScreen`,props:{comparison:{required:!1,tsType:{name:`signature`,type:`object`,raw:`{
  runId: string;
  status: string;
  offers: OfferSummary[];
  sortOptions: string[];
  selectedOfferId: string | null;
  commitBlocked: boolean;
  fallbackReason?: string | null;
  requiredFacts?: string[];
  backendRefs?: string[];
  uiTraceId: string;
  events: string[];
}`,signature:{properties:[{key:`runId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`offers`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
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
}`,signature:{properties:[{key:`offerId`,value:{name:`string`,required:!0}},{key:`rank`,value:{name:`number`,required:!0}},{key:`productLabel`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`productFamily`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`investor`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rate`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`payment`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`apr`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`confidence`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`rankScore`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`lockPeriodDays`,value:{name:`union`,raw:`string | number | null`,elements:[{name:`string`},{name:`number`},{name:`null`}],required:!1}},{key:`eligibilityStatus`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`rationaleChips`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`scenarioFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`explanationStatus`,value:{name:`union`,raw:`'AVAILABLE' | 'MISSING' | 'BLOCKED' | string`,elements:[{name:`literal`,value:`'AVAILABLE'`},{name:`literal`,value:`'MISSING'`},{name:`literal`,value:`'BLOCKED'`},{name:`string`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`sourceScenarioId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`scenarioVersion`,value:{name:`union`,raw:`number | null`,elements:[{name:`number`},{name:`null`}],required:!1}},{key:`upstreamRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`lockEligibilityRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`snapshotRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`auditIds`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`explanationSections`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}}]}}],raw:`OfferSummary[]`,required:!0}},{key:`sortOptions`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`selectedOfferId`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!0}},{key:`commitBlocked`,value:{name:`boolean`,required:!0}},{key:`fallbackReason`,value:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}],required:!1}},{key:`requiredFacts`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`backendRefs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!1}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},description:``,defaultValue:{value:`{
  runId: 'run-preview-001',
  status: 'READY_WITH_FIXTURE_DATA',
  selectedOfferId: null,
  commitBlocked: false,
  sortOptions: ['rank', 'rate', 'apr', 'payment', 'confidence', 'rankScore'],
  fallbackReason: null,
  requiredFacts: [],
  backendRefs: ['quote-service:fixture-unavailable', 'pricing-service:fixture-unavailable'],
  uiTraceId: 'pii-24-s10-local-fixture',
  events: ['fixture:offer-comparison-rendered'],
  offers: [
    {
      offerId: 'offer-a',
      rank: 1,
      productLabel: 'Conventional 30 year fixed',
      productFamily: 'Conventional',
      investor: 'Investor A',
      rate: 6.5,
      apr: 6.74,
      payment: 2104,
      confidence: 96,
      rankScore: 98,
      lockPeriodDays: 30,
      eligibilityStatus: 'Eligible',
      rationaleChips: ['Backend rank 1', 'PRICE_UNAVAILABLE guarded fallback'],
      scenarioFlags: ['CASH_OUT'],
      explanationStatus: 'AVAILABLE',
      explanationSections: ['Pricing summary', 'Eligibility refs'],
      upstreamRefs: ['quote-service:offers'],
      lockEligibilityRefs: ['eligibility-service:lock-ready'],
      snapshotRefs: ['snapshot:offer-a'],
      auditIds: ['audit:offer-a'],
      sourceScenarioId: 'scenario-fixture',
      scenarioVersion: 1,
    },
    {
      offerId: 'offer-b',
      rank: 2,
      productLabel: 'FHA fixed',
      productFamily: 'FHA',
      investor: 'Investor B',
      rate: null,
      apr: null,
      payment: null,
      confidence: 72,
      rankScore: 81,
      lockPeriodDays: 45,
      eligibilityStatus: 'Needs facts',
      rationaleChips: ['PRICE_UNAVAILABLE', 'Backend requires missing facts'],
      scenarioFlags: ['MI_LTV_80'],
      explanationStatus: 'BLOCKED',
      commitBlocked: true,
      requiredFacts: ['pricing-service quote price', 'eligibility lock facts'],
      upstreamRefs: ['pricing-service:missing-price'],
      lockEligibilityRefs: [],
      snapshotRefs: ['snapshot:offer-b'],
      auditIds: ['audit:offer-b'],
      sourceScenarioId: 'scenario-fixture',
      scenarioVersion: 1,
    },
  ],
}`,computed:!1}},onNavigate:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(path: string) => void`,signature:{arguments:[{type:{name:`string`},name:`path`}],return:{name:`void`}}},description:``},tenantId:{defaultValue:{value:`'tenant-fixture'`,computed:!1},required:!1},uiTraceId:{defaultValue:{value:`'pii-24-s10-local-trace'`,computed:!1},required:!1}}}}))();export{J as default};