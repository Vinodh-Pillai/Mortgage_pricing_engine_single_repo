import{i as e,s as t}from"./preload-helper-xPQekRTU.js";import{t as n}from"./iframe-jOsZz4gJ.js";import{t as r}from"./jsx-runtime-CaZkqeYb.js";async function i(e,t,n=fetch){let r=t?`?status=${encodeURIComponent(t)}`:``,i=await n(`/api/v1/partners/${encodeURIComponent(e)}/quotes${r}`,{headers:s});if(i.status>=500)throw Error(`BFF partner quote list boundary is temporarily unavailable.`);return await i.json()}async function a(e,t,n=fetch){let r=await n(`/api/v1/partners/${encodeURIComponent(e)}/quotes/${encodeURIComponent(t)}`,{headers:s});if(r.status>=500)throw Error(`BFF partner quote detail boundary is temporarily unavailable.`);return await r.json()}async function o(e,t,n=fetch){return await(await n(`/api/v1/partners/${encodeURIComponent(e)}/quotes/${encodeURIComponent(t)}/reprice`,{method:`POST`,headers:{...s,"Content-Type":`application/json`},body:JSON.stringify({requestedBy:`partner-workbench`})})).json()}var s,c=e((()=>{s={Accept:`application/json`,"X-Ui-Trace-Id":`ch-s02-local-trace`,"X-Tenant-Context":`ui-preview-tenant`}}));function l({quote:e,result:t,onClose:n,onRequest:r}){return(0,u.jsxs)(`section`,{className:`panel`,role:`dialog`,"aria-modal":`true`,"aria-labelledby":`reprice-modal-heading`,children:[(0,u.jsxs)(`div`,{className:`panel-heading-row`,children:[(0,u.jsxs)(`div`,{children:[(0,u.jsx)(`p`,{className:`eyebrow`,children:`Reprice guidance`}),(0,u.jsx)(`h2`,{id:`reprice-modal-heading`,children:`Request Reprice`})]}),(0,u.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:n,children:`Close`})]}),(0,u.jsxs)(`dl`,{className:`status-grid`,children:[(0,u.jsx)(`dt`,{children:`Current quote`}),(0,u.jsx)(`dd`,{children:(0,u.jsx)(`code`,{children:e.quoteId})}),(0,u.jsx)(`dt`,{children:`Requested by`}),(0,u.jsx)(`dd`,{children:(0,u.jsx)(`code`,{children:e.requestedBy})}),(0,u.jsx)(`dt`,{children:`Partner`}),(0,u.jsx)(`dd`,{children:e.partner}),(0,u.jsx)(`dt`,{children:`Status`}),(0,u.jsx)(`dd`,{children:e.status}),(0,u.jsx)(`dt`,{children:`Guidance`}),(0,u.jsx)(`dd`,{children:e.actions.reprice.guidance||e.guidance}),(0,u.jsx)(`dt`,{children:`Support handoff route`}),(0,u.jsx)(`dd`,{children:(0,u.jsx)(`code`,{children:e.actions.reprice.supportHandoffRoute})})]}),(0,u.jsx)(`p`,{className:`field-help`,children:`This action records a partner reprice request through the configured API contract; pricing calculations remain service-owned. The browser does not calculate prices, rates, margins, or SLA values.`}),(0,u.jsx)(`button`,{type:`button`,onClick:r,disabled:!e.actions.reprice.permitted,children:`Request Reprice`}),e.actions.reprice.permitted?null:(0,u.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:`Reprice requires a permitted partner quote action from the configured API response.`}),t?(0,u.jsxs)(`div`,{className:t.status===`ACCEPTED`?`banner banner--success`:`banner banner--blocked`,role:t.status===`ACCEPTED`?`status`:`alert`,children:[(0,u.jsx)(`strong`,{children:t.status}),(0,u.jsx)(`span`,{children:t.message}),(0,u.jsx)(`span`,{children:t.guidance}),(0,u.jsx)(`span`,{children:(0,u.jsx)(`code`,{children:t.supportHandoffRoute})})]}):null]})}var u,d=e((()=>{u=r(),l.__docgenInfo={description:``,methods:[],displayName:`RepriceModal`,props:{quote:{required:!0,tsType:{name:`intersection`,raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'> & PartnerQuoteRow`,elements:[{name:`Omit`,elements:[{name:`intersection`,raw:`PartnerQuoteSummary & {
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`partnerId`,value:{name:`string`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`actions`,value:{name:`signature`,type:`object`,raw:`{
  reprice: PartnerQuoteAction;
}`,signature:{properties:[{key:`reprice`,value:{name:`signature`,type:`object`,raw:`{
  visible: boolean;
  permitted: boolean;
  guidance: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`visible`,value:{name:`boolean`,required:!0}},{key:`permitted`,value:{name:`boolean`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]},required:!0}}]},required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}}]}}]},{name:`literal`,value:`'lifecycleEvents'`}],raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'>`},{name:`intersection`,raw:`PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`createdRef`,value:{name:`string`,required:!0}},{key:`updatedRef`,value:{name:`string`,required:!0}},{key:`requestedBy`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`errorDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestampRef`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}}]}}],raw:`PartnerQuoteLifecycleEvent[]`,required:!0}},{key:`slaTargetRef`,value:{name:`string`,required:!0}},{key:`slaElapsedRef`,value:{name:`string`,required:!0}},{key:`slaRemainingRef`,value:{name:`string`,required:!0}},{key:`breachPredictionRef`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]}}]}]},description:``},result:{required:!0,tsType:{name:`union`,raw:`PartnerRepriceResult | null`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  status: 'BLOCKED' | 'ACCEPTED' | string;
  message: string;
  guidance: string;
  supportHandoffRoute: string;
  uiTraceId: string;
  events: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'BLOCKED' | 'ACCEPTED' | string`,elements:[{name:`literal`,value:`'BLOCKED'`},{name:`literal`,value:`'ACCEPTED'`},{name:`string`}],required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`null`}]},description:``},onClose:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``},onRequest:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function f({detail:e,loading:t,blockedMessage:n,showRepriceModal:r,repriceResult:i,onOpenReprice:a,onCloseReprice:o,onRequestReprice:s}){return t?(0,p.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quote-detail-heading`,children:[(0,p.jsx)(`h2`,{id:`partner-quote-detail-heading`,children:`Partner Quote Detail`}),(0,p.jsx)(`p`,{role:`status`,children:`Loading partner quote detail...`})]}):n?(0,p.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quote-detail-heading`,children:[(0,p.jsx)(`h2`,{id:`partner-quote-detail-heading`,children:`Partner Quote Detail`}),(0,p.jsx)(`div`,{className:`banner banner--blocked`,role:`alert`,children:n})]}):e?(0,p.jsxs)(`aside`,{className:`panel`,"aria-labelledby":`partner-quote-detail-heading`,children:[(0,p.jsxs)(`div`,{className:`panel-heading-row`,children:[(0,p.jsxs)(`div`,{children:[(0,p.jsx)(`p`,{className:`eyebrow`,children:`Side panel`}),(0,p.jsx)(`h2`,{id:`partner-quote-detail-heading`,children:`Partner Quote Detail`})]}),(0,p.jsx)(`button`,{type:`button`,onClick:a,children:`Reprice`})]}),(0,p.jsxs)(`dl`,{className:`status-grid`,children:[(0,p.jsx)(`dt`,{children:`Quote ID`}),(0,p.jsx)(`dd`,{children:(0,p.jsx)(`code`,{children:e.quoteId})}),(0,p.jsx)(`dt`,{children:`Partner`}),(0,p.jsx)(`dd`,{children:e.partner}),(0,p.jsx)(`dt`,{children:`Borrower`}),(0,p.jsx)(`dd`,{children:e.borrowerLabel}),(0,p.jsx)(`dt`,{children:`Status`}),(0,p.jsx)(`dd`,{children:e.status}),(0,p.jsx)(`dt`,{children:`SLA`}),(0,p.jsx)(`dd`,{children:e.slaState}),(0,p.jsx)(`dt`,{children:`Lock`}),(0,p.jsx)(`dd`,{children:e.lockState})]}),(0,p.jsxs)(`section`,{"aria-labelledby":`sla-tracking-heading`,children:[(0,p.jsx)(`h3`,{id:`sla-tracking-heading`,children:`SLA tracking refs`}),(0,p.jsxs)(`dl`,{className:`status-grid`,children:[(0,p.jsx)(`dt`,{children:`Target`}),(0,p.jsx)(`dd`,{children:(0,p.jsx)(`code`,{children:e.slaTargetRef})}),(0,p.jsx)(`dt`,{children:`Elapsed`}),(0,p.jsx)(`dd`,{children:(0,p.jsx)(`code`,{children:e.slaElapsedRef})}),(0,p.jsx)(`dt`,{children:`Remaining`}),(0,p.jsx)(`dd`,{children:(0,p.jsx)(`code`,{children:e.slaRemainingRef})}),(0,p.jsx)(`dt`,{children:`Breach prediction`}),(0,p.jsx)(`dd`,{children:(0,p.jsx)(`code`,{children:e.breachPredictionRef})})]})]}),(0,p.jsxs)(`section`,{"aria-labelledby":`error-details-heading`,children:[(0,p.jsx)(`h3`,{id:`error-details-heading`,children:`Error details`}),e.errorDetails.length===0?(0,p.jsx)(`p`,{role:`status`,children:`No error flags supplied for this quote.`}):(0,p.jsx)(`ul`,{children:e.errorDetails.map(e=>(0,p.jsx)(`li`,{children:(0,p.jsx)(`code`,{children:e})},e))})]}),(0,p.jsxs)(`section`,{"aria-labelledby":`lifecycle-timeline-heading`,children:[(0,p.jsx)(`h3`,{id:`lifecycle-timeline-heading`,children:`Lifecycle events`}),(0,p.jsxs)(`div`,{className:`quote-table`,role:`table`,"aria-label":`Partner quote lifecycle timeline`,children:[(0,p.jsxs)(`div`,{role:`row`,className:`quote-table__row quote-table__row--head`,children:[(0,p.jsx)(`span`,{role:`columnheader`,children:`Timestamp`}),(0,p.jsx)(`span`,{role:`columnheader`,children:`Event type`}),(0,p.jsx)(`span`,{role:`columnheader`,children:`Summary`})]}),e.lifecycleEvents.map(e=>(0,p.jsxs)(`div`,{role:`row`,className:`quote-table__row`,children:[(0,p.jsx)(`span`,{role:`cell`,children:(0,p.jsx)(`code`,{children:e.timestampRef})}),(0,p.jsx)(`span`,{role:`cell`,children:e.eventType}),(0,p.jsx)(`span`,{role:`cell`,children:e.summary})]},e.eventId))]})]}),r?(0,p.jsx)(l,{quote:e,result:i,onClose:o,onRequest:s}):null]}):(0,p.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quote-detail-heading`,children:[(0,p.jsx)(`h2`,{id:`partner-quote-detail-heading`,children:`Partner Quote Detail`}),(0,p.jsx)(`p`,{children:`Select a quote row to inspect lifecycle events, SLA refs, lock state, and reprice guidance.`})]})}var p,m=e((()=>{d(),p=r(),f.__docgenInfo={description:``,methods:[],displayName:`QuoteDetail`,props:{detail:{required:!0,tsType:{name:`union`,raw:`PartnerQuoteDetailView | null`,elements:[{name:`intersection`,raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'> & PartnerQuoteRow`,elements:[{name:`Omit`,elements:[{name:`intersection`,raw:`PartnerQuoteSummary & {
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`partnerId`,value:{name:`string`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`actions`,value:{name:`signature`,type:`object`,raw:`{
  reprice: PartnerQuoteAction;
}`,signature:{properties:[{key:`reprice`,value:{name:`signature`,type:`object`,raw:`{
  visible: boolean;
  permitted: boolean;
  guidance: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`visible`,value:{name:`boolean`,required:!0}},{key:`permitted`,value:{name:`boolean`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]},required:!0}}]},required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}}]}}]},{name:`literal`,value:`'lifecycleEvents'`}],raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'>`},{name:`intersection`,raw:`PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`createdRef`,value:{name:`string`,required:!0}},{key:`updatedRef`,value:{name:`string`,required:!0}},{key:`requestedBy`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`errorDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestampRef`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}}]}}],raw:`PartnerQuoteLifecycleEvent[]`,required:!0}},{key:`slaTargetRef`,value:{name:`string`,required:!0}},{key:`slaElapsedRef`,value:{name:`string`,required:!0}},{key:`slaRemainingRef`,value:{name:`string`,required:!0}},{key:`breachPredictionRef`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]}}]}]},{name:`null`}]},description:``},loading:{required:!0,tsType:{name:`boolean`},description:``},blockedMessage:{required:!0,tsType:{name:`string`},description:``},showRepriceModal:{required:!0,tsType:{name:`boolean`},description:``},repriceResult:{required:!0,tsType:{name:`union`,raw:`PartnerRepriceResult | null`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  status: 'BLOCKED' | 'ACCEPTED' | string;
  message: string;
  guidance: string;
  supportHandoffRoute: string;
  uiTraceId: string;
  events: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`union`,raw:`'BLOCKED' | 'ACCEPTED' | string`,elements:[{name:`literal`,value:`'BLOCKED'`},{name:`literal`,value:`'ACCEPTED'`},{name:`string`}],required:!0}},{key:`message`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`null`}]},description:``},onOpenReprice:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``},onCloseReprice:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``},onRequestReprice:{required:!0,tsType:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}}},description:``}}}}));function h({quotes:e,filters:t,selectedQuoteId:n,onFiltersChange:r,onSelectQuote:i}){let a=v(e.map(e=>e.partner)),o=g(e,t);return(0,y.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quote-list-heading`,children:[(0,y.jsx)(`div`,{className:`panel-heading-row`,children:(0,y.jsxs)(`div`,{children:[(0,y.jsx)(`p`,{className:`eyebrow`,children:`Quote list`}),(0,y.jsx)(`h2`,{id:`partner-quote-list-heading`,children:`Partner quote list`})]})}),(0,y.jsxs)(`div`,{className:`offer-toolbar`,"aria-label":`Partner quote filters`,children:[(0,y.jsx)(_,{label:`Partner filter`,value:t.partner,values:a,onChange:e=>r({...t,partner:e})}),(0,y.jsx)(_,{label:`Status filter`,value:t.status,values:[`SUBMITTED`,`PRICING`,`PRICED`,`LOCKED`,`COMMITTED`,`FUNDED`,`REJECTED`,`WITHDRAWN`],onChange:e=>r({...t,status:e})}),(0,y.jsx)(_,{label:`SLA state filter`,value:t.slaState,values:[`ON_TRACK`,`AT_RISK`,`BREACHED`],onChange:e=>r({...t,slaState:e})}),(0,y.jsx)(_,{label:`Lock state filter`,value:t.lockState,values:[`UNLOCKED`,`LOCKED`,`EXPIRED`,`RELOCKED`,`FLOAT_DOWN`],onChange:e=>r({...t,lockState:e})}),(0,y.jsxs)(`label`,{children:[`Date range`,(0,y.jsxs)(`select`,{value:t.dateRange,onChange:e=>r({...t,dateRange:e.target.value}),children:[(0,y.jsx)(`option`,{value:`all`,children:`All refs`}),(0,y.jsx)(`option`,{value:`created-ref`,children:`Created refs`}),(0,y.jsx)(`option`,{value:`updated-ref`,children:`Updated refs`})]})]}),(0,y.jsxs)(`label`,{children:[`Sort`,(0,y.jsxs)(`select`,{value:t.sort,onChange:e=>r({...t,sort:e.target.value}),children:[(0,y.jsx)(`option`,{value:`created`,children:`Created`}),(0,y.jsx)(`option`,{value:`updated`,children:`Updated`}),(0,y.jsx)(`option`,{value:`sla`,children:`SLA`}),(0,y.jsx)(`option`,{value:`borrower`,children:`Borrower`})]})]})]}),o.length===0?(0,y.jsx)(`p`,{role:`status`,children:`No partner quotes match the selected filters.`}):(0,y.jsxs)(`div`,{className:`quote-table`,role:`table`,"aria-label":`Partner quotes list`,children:[(0,y.jsxs)(`div`,{role:`row`,className:`quote-table__row quote-table__row--head`,children:[(0,y.jsx)(`span`,{role:`columnheader`,children:`Quote ID`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Borrower Label`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Status`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`SLA State`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Lock State`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Error Flags`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Partner`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Created`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Updated`}),(0,y.jsx)(`span`,{role:`columnheader`,children:`Action`})]}),o.map(e=>(0,y.jsxs)(`div`,{role:`row`,className:n===e.quoteId?`quote-table__row quote-table__row--selected`:`quote-table__row`,children:[(0,y.jsx)(`span`,{role:`cell`,children:(0,y.jsx)(`code`,{children:e.quoteId})}),(0,y.jsx)(`span`,{role:`cell`,children:e.borrowerLabel}),(0,y.jsx)(`span`,{role:`cell`,children:e.status}),(0,y.jsx)(`span`,{role:`cell`,children:e.slaState}),(0,y.jsx)(`span`,{role:`cell`,children:e.lockState}),(0,y.jsx)(`span`,{role:`cell`,children:(0,y.jsx)(`button`,{type:`button`,className:`button-secondary`,onClick:()=>i(e.quoteId),children:e.errorFlags.length?e.errorFlags.join(`, `):`No error flags`})}),(0,y.jsx)(`span`,{role:`cell`,children:e.partner}),(0,y.jsx)(`span`,{role:`cell`,children:(0,y.jsx)(`code`,{children:e.createdRef})}),(0,y.jsx)(`span`,{role:`cell`,children:(0,y.jsx)(`code`,{children:e.updatedRef})}),(0,y.jsx)(`span`,{role:`cell`,children:(0,y.jsx)(`button`,{type:`button`,onClick:()=>i(e.quoteId),children:`Open quote detail`})})]},e.quoteId))]})]})}function g(e,t){return e.filter(e=>t.partner===`all`||e.partner===t.partner).filter(e=>t.status===`all`||e.status===t.status).filter(e=>t.slaState===`all`||e.slaState===t.slaState).filter(e=>t.lockState===`all`||e.lockState===t.lockState).filter(e=>t.dateRange===`all`||e.createdRef.includes(t.dateRange)||e.updatedRef.includes(t.dateRange)).sort((e,n)=>t.sort===`updated`?e.updatedRef.localeCompare(n.updatedRef):t.sort===`sla`?e.slaState.localeCompare(n.slaState):t.sort===`borrower`?e.borrowerLabel.localeCompare(n.borrowerLabel):e.createdRef.localeCompare(n.createdRef))}function _({label:e,value:t,values:n,onChange:r}){return(0,y.jsxs)(`label`,{children:[e,(0,y.jsxs)(`select`,{value:t,onChange:e=>r(e.target.value),children:[(0,y.jsx)(`option`,{value:`all`,children:`All`}),n.map(e=>(0,y.jsx)(`option`,{value:e,children:e},e))]})]})}function v(e){return Array.from(new Set(e))}var y,b=e((()=>{y=r(),h.__docgenInfo={description:``,methods:[],displayName:`QuoteList`,props:{quotes:{required:!0,tsType:{name:`Array`,elements:[{name:`intersection`,raw:`PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`createdRef`,value:{name:`string`,required:!0}},{key:`updatedRef`,value:{name:`string`,required:!0}},{key:`requestedBy`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`errorDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestampRef`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}}]}}],raw:`PartnerQuoteLifecycleEvent[]`,required:!0}},{key:`slaTargetRef`,value:{name:`string`,required:!0}},{key:`slaElapsedRef`,value:{name:`string`,required:!0}},{key:`slaRemainingRef`,value:{name:`string`,required:!0}},{key:`breachPredictionRef`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]}}]}],raw:`PartnerQuoteRow[]`},description:``},filters:{required:!0,tsType:{name:`signature`,type:`object`,raw:`{
  partner: string;
  status: string;
  slaState: string;
  lockState: string;
  dateRange: string;
  sort: 'created' | 'updated' | 'sla' | 'borrower';
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`dateRange`,value:{name:`string`,required:!0}},{key:`sort`,value:{name:`union`,raw:`'created' | 'updated' | 'sla' | 'borrower'`,elements:[{name:`literal`,value:`'created'`},{name:`literal`,value:`'updated'`},{name:`literal`,value:`'sla'`},{name:`literal`,value:`'borrower'`}],required:!0}}]}},description:``},selectedQuoteId:{required:!0,tsType:{name:`union`,raw:`string | null`,elements:[{name:`string`},{name:`null`}]},description:``},onFiltersChange:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(filters: PartnerQuoteFilters) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  partner: string;
  status: string;
  slaState: string;
  lockState: string;
  dateRange: string;
  sort: 'created' | 'updated' | 'sla' | 'borrower';
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`dateRange`,value:{name:`string`,required:!0}},{key:`sort`,value:{name:`union`,raw:`'created' | 'updated' | 'sla' | 'borrower'`,elements:[{name:`literal`,value:`'created'`},{name:`literal`,value:`'updated'`},{name:`literal`,value:`'sla'`},{name:`literal`,value:`'borrower'`}],required:!0}}]}},name:`filters`}],return:{name:`void`}}},description:``},onSelectQuote:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(quoteId: string) => void`,signature:{arguments:[{type:{name:`string`},name:`quoteId`}],return:{name:`void`}}},description:``}}}}));function x(e,t=`partner-fixture`){return{...e,dependencyStatus:e.dependencyStatus??`PARTNER_QUOTES_READ_MODEL_SUPPLIED`,fallbackReason:e.fallbackReason??`UI renders partner quote refs from backend-shaped data or deterministic fixtures only; no pricing or SLA calculation is performed in the browser.`,quotes:e.quotes.map(e=>C(e,t))}}function S(e,t=`partner-fixture`){let n=C(e,t);return{...e,...n,tenantContext:e.tenantContext,partnerId:e.partnerId,actions:e.actions,uiTraceId:e.uiTraceId}}function C(e,t=`partner-fixture`){let n=e,r=w(n.lifecycleEvents??[]);return{...e,partner:n.partner??t,createdRef:n.createdRef??`created-ref:${e.quoteId}`,updatedRef:n.updatedRef??`updated-ref:${e.quoteId}`,requestedBy:n.requestedBy??`requested-by-ref:not-supplied`,guidance:n.guidance??`Reprice requires an explicit partner quote action contract; the UI does not calculate price changes.`,errorDetails:n.errorDetails??e.errorFlags.map(e=>`error-detail-ref:${e}`),lifecycleEvents:r.length?r:[{eventId:`${e.quoteId}-status`,eventType:e.status,timestampRef:`timestamp-ref:not-supplied`,summary:`Lifecycle event summary is supplied by partner quote detail when available.`}],slaTargetRef:n.slaTargetRef??`sla-target-ref:not-supplied`,slaElapsedRef:n.slaElapsedRef??`sla-elapsed-ref:not-supplied`,slaRemainingRef:n.slaRemainingRef??`sla-remaining-ref:not-supplied`,breachPredictionRef:n.breachPredictionRef??`sla-breach-prediction-ref:not-supplied`,supportHandoffRoute:n.supportHandoffRoute??`/partners/support/reprice`}}function w(e){return e.map((e,t)=>typeof e==`string`?{eventId:`event-${t+1}`,eventType:e,timestampRef:`timestamp-ref:not-supplied`,summary:e}:e)}var T,E=e((()=>{T={partner:`all`,status:`all`,slaState:`all`,lockState:`all`,dateRange:`all`,sort:`created`}}));function D({tenantContext:e=N,partnerId:t=P,evidence:n,detailEvidence:r,fetchImpl:s,onEvidenceCapture:c}){let[l,u]=(0,A.useState)(T),[d,p]=(0,A.useState)(()=>n?{kind:`loaded`,view:x(n,t)}:{kind:`loading`}),[m,g]=(0,A.useState)(()=>n?.quotes[0]?.quoteId??r?.quoteId??null),[_,v]=(0,A.useState)(()=>r?{kind:`loaded`,detail:S(r,t)}:{kind:`idle`}),[y,b]=(0,A.useState)(!1),[C,w]=(0,A.useState)(null);(0,A.useEffect)(()=>{if(n){let e=x(n,t);p({kind:`loaded`,view:e}),g(t=>t??e.quotes[0]?.quoteId??null);return}let e=!0;return p({kind:`loading`}),i(t,l.status===`all`?``:l.status,s).then(n=>{if(!e)return;let r=x(n,t);p({kind:`loaded`,view:r}),g(e=>e&&r.quotes.some(t=>t.quoteId===e)?e:r.quotes[0]?.quoteId??null)}).catch(t=>{let n=t instanceof Error?t.message:`Partner quote list is unavailable.`;e&&p({kind:`unreachable`,message:n})}),()=>{e=!1}},[n,s,l.status,t]),(0,A.useEffect)(()=>{if(r){v({kind:`loaded`,detail:S(r,t)});return}if(!m){v({kind:`idle`});return}let n=d.kind===`loaded`?d.view:null,i=n?.quotes.find(e=>e.quoteId===m)??null;if(i){v({kind:`loaded`,detail:S({...i,tenantContext:e,partnerId:t,actions:{reprice:{visible:!0,permitted:!0,guidance:i.guidance,supportHandoffRoute:i.supportHandoffRoute}},uiTraceId:n?.uiTraceId??`partner-quotes-s21-local-trace`},t)});return}let o=!0;return v({kind:`loading`}),a(t,m,s).then(e=>{o&&v({kind:`loaded`,detail:S(e,t)})}).catch(e=>{let t=e instanceof Error?e.message:`Partner quote detail is unavailable.`;o&&v({kind:`unreachable`,message:t})}),()=>{o=!1}},[r,s,d,t,m,e]),(0,A.useEffect)(()=>{d.kind===`loaded`&&c?.({screenId:`partner-quotes`,state:O(d.view),evidenceTarget:M,refs:k(d.view)})},[d,c]);let E=d.kind===`loaded`?d.view:null,D=_.kind===`loaded`?_.detail:null,F=d.kind===`unreachable`?d.message:_.kind===`unreachable`?_.message:``,I=E?O(E):F?`blocked`:`load-state`,L=(0,A.useMemo)(()=>E?k(E).slice(0,4):[],[E]);async function R(){D&&w(await o(t,D.quoteId,s))}return(0,j.jsxs)(j.Fragment,{children:[(0,j.jsxs)(`section`,{className:`hero hero--admin`,"aria-labelledby":`partner-quotes-title`,children:[(0,j.jsx)(`p`,{className:`eyebrow`,children:`Partner quotes - PII-24-S21`}),(0,j.jsx)(`h2`,{id:`partner-quotes-title`,children:`Partner Quotes`}),(0,j.jsx)(`p`,{children:`Manage partner-submitted quotes with lifecycle, SLA state, lock state, error flag, and reprice request visibility. Pricing logic and SLA calculations remain service-owned.`})]}),(0,j.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quotes-heading`,children:[(0,j.jsx)(`div`,{className:`panel-heading-row`,children:(0,j.jsxs)(`div`,{children:[(0,j.jsx)(`p`,{className:`eyebrow`,children:`Tenant context`}),(0,j.jsx)(`h2`,{id:`partner-quotes-heading`,children:`Partner quotes workspace`})]})}),(0,j.jsxs)(`dl`,{className:`status-grid`,children:[(0,j.jsx)(`dt`,{children:`Tenant`}),(0,j.jsx)(`dd`,{children:e}),(0,j.jsx)(`dt`,{children:`Partner selector`}),(0,j.jsx)(`dd`,{children:t}),(0,j.jsx)(`dt`,{children:`Status`}),(0,j.jsx)(`dd`,{children:E?.dependencyStatus??I}),(0,j.jsx)(`dt`,{children:`Support reference`}),(0,j.jsx)(`dd`,{children:(0,j.jsx)(`code`,{children:E?.uiTraceId??`partner-quotes-s21-local-trace`})}),(0,j.jsx)(`dt`,{children:`Evidence target`}),(0,j.jsx)(`dd`,{children:(0,j.jsx)(`code`,{children:M})})]}),(0,j.jsxs)(`div`,{className:I===`blocked`?`banner banner--blocked`:`banner banner--info`,role:I===`blocked`?`alert`:`status`,children:[(0,j.jsx)(`strong`,{children:E?.dependencyStatus??`Loading partner quote read model`}),(0,j.jsx)(`span`,{children:E?.fallbackReason??`Waiting for partner quote refs.`})]}),L.length?(0,j.jsx)(`ul`,{className:`chip-list`,"aria-label":`Partner quote evidence refs`,children:L.map(e=>(0,j.jsx)(`li`,{children:e},e))}):null]}),d.kind===`loading`?(0,j.jsxs)(`section`,{className:`panel`,"aria-labelledby":`partner-quote-list-heading`,children:[(0,j.jsx)(`h2`,{id:`partner-quote-list-heading`,children:`Partner quote list`}),(0,j.jsx)(`p`,{role:`status`,children:`Loading partner quotes...`})]}):null,E?(0,j.jsx)(h,{quotes:E.quotes,filters:l,selectedQuoteId:m,onFiltersChange:u,onSelectQuote:e=>{g(e),b(!1),w(null)}}):null,(0,j.jsx)(f,{detail:D,loading:_.kind===`loading`,blockedMessage:F,showRepriceModal:y,repriceResult:C,onOpenReprice:()=>b(!0),onCloseReprice:()=>b(!1),onRequestReprice:()=>void R()})]})}function O(e){return(e.dependencyStatus??``).toLowerCase().includes(`blocked`)?`blocked`:e.quotes.length===0?`empty`:`ready`}function k(e){return e.quotes.flatMap(e=>[e.quoteId,e.createdRef,e.updatedRef,...e.errorFlags,...e.lifecycleEvents.map(e=>e.eventId)])}var A,j,M,N,P;e((()=>{A=t(n(),1),c(),m(),b(),E(),j=r(),M=`.local-harness/evidence/PII-24-S21/partner-quotes.json`,N=`ui-preview-tenant`,P=`partner-fixture`,D.__docgenInfo={description:``,methods:[],displayName:`PartnerQuotesLayout`,props:{tenantContext:{required:!1,tsType:{name:`string`},description:``,defaultValue:{value:`'ui-preview-tenant'`,computed:!1}},partnerId:{required:!1,tsType:{name:`string`},description:``,defaultValue:{value:`'partner-fixture'`,computed:!1}},evidence:{required:!1,tsType:{name:`intersection`,raw:`Omit<PartnerQuoteListView, 'quotes'> & {
  dependencyStatus?: string;
  fallbackReason?: string;
  quotes: PartnerQuoteRow[];
}`,elements:[{name:`Omit`,elements:[{name:`signature`,type:`object`,raw:`{
  partnerId: string;
  tenantContext: string;
  statusFilter: string;
  quotes: PartnerQuoteSummary[];
  uiTraceId: string;
  events: string[];
}`,signature:{properties:[{key:`partnerId`,value:{name:`string`,required:!0}},{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`statusFilter`,value:{name:`string`,required:!0}},{key:`quotes`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}}],raw:`PartnerQuoteSummary[]`,required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}},{key:`events`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`literal`,value:`'quotes'`}],raw:`Omit<PartnerQuoteListView, 'quotes'>`},{name:`signature`,type:`object`,raw:`{
  dependencyStatus?: string;
  fallbackReason?: string;
  quotes: PartnerQuoteRow[];
}`,signature:{properties:[{key:`dependencyStatus`,value:{name:`string`,required:!1}},{key:`fallbackReason`,value:{name:`string`,required:!1}},{key:`quotes`,value:{name:`Array`,elements:[{name:`intersection`,raw:`PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`createdRef`,value:{name:`string`,required:!0}},{key:`updatedRef`,value:{name:`string`,required:!0}},{key:`requestedBy`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`errorDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestampRef`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}}]}}],raw:`PartnerQuoteLifecycleEvent[]`,required:!0}},{key:`slaTargetRef`,value:{name:`string`,required:!0}},{key:`slaElapsedRef`,value:{name:`string`,required:!0}},{key:`slaRemainingRef`,value:{name:`string`,required:!0}},{key:`breachPredictionRef`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]}}]}],raw:`PartnerQuoteRow[]`,required:!0}}]}}]},description:``},detailEvidence:{required:!1,tsType:{name:`intersection`,raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'> & PartnerQuoteRow`,elements:[{name:`Omit`,elements:[{name:`intersection`,raw:`PartnerQuoteSummary & {
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  tenantContext: string;
  partnerId: string;
  lifecycleEvents: string[];
  actions: {
    reprice: PartnerQuoteAction;
  };
  uiTraceId: string;
}`,signature:{properties:[{key:`tenantContext`,value:{name:`string`,required:!0}},{key:`partnerId`,value:{name:`string`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`actions`,value:{name:`signature`,type:`object`,raw:`{
  reprice: PartnerQuoteAction;
}`,signature:{properties:[{key:`reprice`,value:{name:`signature`,type:`object`,raw:`{
  visible: boolean;
  permitted: boolean;
  guidance: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`visible`,value:{name:`boolean`,required:!0}},{key:`permitted`,value:{name:`boolean`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]},required:!0}}]},required:!0}},{key:`uiTraceId`,value:{name:`string`,required:!0}}]}}]},{name:`literal`,value:`'lifecycleEvents'`}],raw:`Omit<PartnerQuoteDetail, 'lifecycleEvents'>`},{name:`intersection`,raw:`PartnerQuoteSummary & {
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteId: string;
  borrowerLabel: string;
  status: string;
  slaState: string;
  lockState: string;
  errorFlags: string[];
}`,signature:{properties:[{key:`quoteId`,value:{name:`string`,required:!0}},{key:`borrowerLabel`,value:{name:`string`,required:!0}},{key:`status`,value:{name:`string`,required:!0}},{key:`slaState`,value:{name:`string`,required:!0}},{key:`lockState`,value:{name:`string`,required:!0}},{key:`errorFlags`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},{name:`signature`,type:`object`,raw:`{
  partner: string;
  createdRef: string;
  updatedRef: string;
  requestedBy: string;
  guidance: string;
  errorDetails: string[];
  lifecycleEvents: PartnerQuoteLifecycleEvent[];
  slaTargetRef: string;
  slaElapsedRef: string;
  slaRemainingRef: string;
  breachPredictionRef: string;
  supportHandoffRoute: string;
}`,signature:{properties:[{key:`partner`,value:{name:`string`,required:!0}},{key:`createdRef`,value:{name:`string`,required:!0}},{key:`updatedRef`,value:{name:`string`,required:!0}},{key:`requestedBy`,value:{name:`string`,required:!0}},{key:`guidance`,value:{name:`string`,required:!0}},{key:`errorDetails`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}},{key:`lifecycleEvents`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  eventId: string;
  eventType: string;
  timestampRef: string;
  summary: string;
}`,signature:{properties:[{key:`eventId`,value:{name:`string`,required:!0}},{key:`eventType`,value:{name:`string`,required:!0}},{key:`timestampRef`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}}]}}],raw:`PartnerQuoteLifecycleEvent[]`,required:!0}},{key:`slaTargetRef`,value:{name:`string`,required:!0}},{key:`slaElapsedRef`,value:{name:`string`,required:!0}},{key:`slaRemainingRef`,value:{name:`string`,required:!0}},{key:`breachPredictionRef`,value:{name:`string`,required:!0}},{key:`supportHandoffRoute`,value:{name:`string`,required:!0}}]}}]}]},description:``},fetchImpl:{required:!1,tsType:{name:`fetch`},description:``},onEvidenceCapture:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(evidence: { screenId: string; state: string; evidenceTarget: string; refs: string[] }) => void`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{ screenId: string; state: string; evidenceTarget: string; refs: string[] }`,signature:{properties:[{key:`screenId`,value:{name:`string`,required:!0}},{key:`state`,value:{name:`string`,required:!0}},{key:`evidenceTarget`,value:{name:`string`,required:!0}},{key:`refs`,value:{name:`Array`,elements:[{name:`string`}],raw:`string[]`,required:!0}}]}},name:`evidence`}],return:{name:`void`}}},description:``}}}}))();export{k as collectPartnerQuoteRefs,D as default,M as partnerQuotesEvidenceTarget,O as stateForPartnerQuotes};