import{i as e}from"./preload-helper-xPQekRTU.js";import{t}from"./jsx-runtime-CaZkqeYb.js";import{a as n,c as r,i,n as a,s as o,t as s}from"./QuoteIntakeFlow-Dh81XLKY.js";function c({steps:e,activeStep:t,statuses:n,onStepSelect:r}){let i=e.filter(e=>n[e.id]===`complete`).length;return(0,u.jsxs)(`nav`,{className:`quote-intake-progress`,"aria-label":`Quote intake progress`,children:[(0,u.jsx)(`div`,{className:`quote-intake-progress__bar`,role:`progressbar`,"aria-valuemin":1,"aria-valuemax":e.length,"aria-valuenow":t,"aria-valuetext":`Step ${t} of ${e.length}`,children:(0,u.jsx)(`span`,{style:{inlineSize:`${i/e.length*100}%`}})}),(0,u.jsx)(`ol`,{children:e.map(e=>{let i=e.id===t?`in-progress`:n[e.id];return(0,u.jsx)(`li`,{"data-status":i,children:(0,u.jsxs)(`button`,{type:`button`,disabled:!(e.id===t||n[e.id]===`complete`||e.id<t),"aria-current":e.id===t?`step`:void 0,onClick:()=>r(e.id),children:[(0,u.jsx)(`span`,{className:`quote-intake-progress__number`,"aria-hidden":`true`,children:e.id}),(0,u.jsx)(`span`,{children:e.shortLabel}),(0,u.jsx)(`small`,{children:l(i)})]})},e.id)})})]})}function l(e){return e===`in-progress`?`in progress`:e===`complete`?`complete`:e===`error`?`needs attention`:`empty`}var u,d=e((()=>{u=t(),c.__docgenInfo={description:``,methods:[],displayName:`ProgressIndicator`,props:{steps:{required:!0,tsType:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  id: QuoteIntakeStepId;
  section: QuoteIntakeSection;
  label: string;
  shortLabel: string;
  summary: string;
  fallbackFields: Array<keyof BorrowerIntake>;
}`,signature:{properties:[{key:`id`,value:{name:`union`,raw:`1 | 2 | 3 | 4 | 5 | 6`,elements:[{name:`literal`,value:`1`},{name:`literal`,value:`2`},{name:`literal`,value:`3`},{name:`literal`,value:`4`},{name:`literal`,value:`5`},{name:`literal`,value:`6`}],required:!0}},{key:`section`,value:{name:`union`,raw:`'scenario-identity' | 'borrower-credit' | 'loan-structure' | 'property' | 'income-assets' | 'preferences'`,elements:[{name:`literal`,value:`'scenario-identity'`},{name:`literal`,value:`'borrower-credit'`},{name:`literal`,value:`'loan-structure'`},{name:`literal`,value:`'property'`},{name:`literal`,value:`'income-assets'`},{name:`literal`,value:`'preferences'`}],required:!0}},{key:`label`,value:{name:`string`,required:!0}},{key:`shortLabel`,value:{name:`string`,required:!0}},{key:`summary`,value:{name:`string`,required:!0}},{key:`fallbackFields`,value:{name:`Array`,elements:[{name:`signature`,type:`object`,raw:`{
  quoteIntent: string;
  channel: string;
  scenarioName: string;
  externalLoanId: string;
  sourceSystem: string;
  borrowerName: string;
  borrowerRole: string;
  coBorrowerName: string;
  coBorrowerRole: string;
  contactEmail: string;
  creditStatus: string;
  creditScore: string;
  creditScoreSource: string;
  creditReportDate: string;
  creditReadiness: string;
  loanPurpose: string;
  loanAmount: string;
  purchasePriceOrValue: string;
  downPaymentOrEquity: string;
  subordinateFinancingAmount: string;
  helocDrawnAmount: string;
  helocLimitAmount: string;
  lienPosition: string;
  termMonths: string;
  amortizationType: string;
  requestedLockPeriodDays: string;
  propertyState: string;
  propertyCounty: string;
  propertyZip: string;
  propertyType: string;
  occupancyType: string;
  unitCount: string;
  purchasePrice: string;
  appraisedValue: string;
  condoProjectType: string;
  manufacturedHomeFlag: string;
  monthlyIncome: string;
  incomeType: string;
  employmentType: string;
  monthlyDebt: string;
  suppliedDti: string;
  reserveMonths: string;
  incomeVerificationStatus: string;
  assetVerificationStatus: string;
  liquidAssets: string;
  reserves: string;
  productFamily: string;
  productPreference: string;
  quoteFilters: string;
  effectiveDate: string;
  actorId: string;
  clientContext: string;
}`,signature:{properties:[{key:`quoteIntent`,value:{name:`string`,required:!0}},{key:`channel`,value:{name:`string`,required:!0}},{key:`scenarioName`,value:{name:`string`,required:!0}},{key:`externalLoanId`,value:{name:`string`,required:!0}},{key:`sourceSystem`,value:{name:`string`,required:!0}},{key:`borrowerName`,value:{name:`string`,required:!0}},{key:`borrowerRole`,value:{name:`string`,required:!0}},{key:`coBorrowerName`,value:{name:`string`,required:!0}},{key:`coBorrowerRole`,value:{name:`string`,required:!0}},{key:`contactEmail`,value:{name:`string`,required:!0}},{key:`creditStatus`,value:{name:`string`,required:!0}},{key:`creditScore`,value:{name:`string`,required:!0}},{key:`creditScoreSource`,value:{name:`string`,required:!0}},{key:`creditReportDate`,value:{name:`string`,required:!0}},{key:`creditReadiness`,value:{name:`string`,required:!0}},{key:`loanPurpose`,value:{name:`string`,required:!0}},{key:`loanAmount`,value:{name:`string`,required:!0}},{key:`purchasePriceOrValue`,value:{name:`string`,required:!0}},{key:`downPaymentOrEquity`,value:{name:`string`,required:!0}},{key:`subordinateFinancingAmount`,value:{name:`string`,required:!0}},{key:`helocDrawnAmount`,value:{name:`string`,required:!0}},{key:`helocLimitAmount`,value:{name:`string`,required:!0}},{key:`lienPosition`,value:{name:`string`,required:!0}},{key:`termMonths`,value:{name:`string`,required:!0}},{key:`amortizationType`,value:{name:`string`,required:!0}},{key:`requestedLockPeriodDays`,value:{name:`string`,required:!0}},{key:`propertyState`,value:{name:`string`,required:!0}},{key:`propertyCounty`,value:{name:`string`,required:!0}},{key:`propertyZip`,value:{name:`string`,required:!0}},{key:`propertyType`,value:{name:`string`,required:!0}},{key:`occupancyType`,value:{name:`string`,required:!0}},{key:`unitCount`,value:{name:`string`,required:!0}},{key:`purchasePrice`,value:{name:`string`,required:!0}},{key:`appraisedValue`,value:{name:`string`,required:!0}},{key:`condoProjectType`,value:{name:`string`,required:!0}},{key:`manufacturedHomeFlag`,value:{name:`string`,required:!0}},{key:`monthlyIncome`,value:{name:`string`,required:!0}},{key:`incomeType`,value:{name:`string`,required:!0}},{key:`employmentType`,value:{name:`string`,required:!0}},{key:`monthlyDebt`,value:{name:`string`,required:!0}},{key:`suppliedDti`,value:{name:`string`,required:!0}},{key:`reserveMonths`,value:{name:`string`,required:!0}},{key:`incomeVerificationStatus`,value:{name:`string`,required:!0}},{key:`assetVerificationStatus`,value:{name:`string`,required:!0}},{key:`liquidAssets`,value:{name:`string`,required:!0}},{key:`reserves`,value:{name:`string`,required:!0}},{key:`productFamily`,value:{name:`string`,required:!0}},{key:`productPreference`,value:{name:`string`,required:!0}},{key:`quoteFilters`,value:{name:`string`,required:!0}},{key:`effectiveDate`,value:{name:`string`,required:!0}},{key:`actorId`,value:{name:`string`,required:!0}},{key:`clientContext`,value:{name:`string`,required:!0}}]}}],raw:`Array<keyof BorrowerIntake>`,required:!0}}]}}],raw:`QuoteIntakeStepDefinition[]`},description:``},activeStep:{required:!0,tsType:{name:`union`,raw:`1 | 2 | 3 | 4 | 5 | 6`,elements:[{name:`literal`,value:`1`},{name:`literal`,value:`2`},{name:`literal`,value:`3`},{name:`literal`,value:`4`},{name:`literal`,value:`5`},{name:`literal`,value:`6`}]},description:``},statuses:{required:!0,tsType:{name:`Record`,elements:[{name:`union`,raw:`1 | 2 | 3 | 4 | 5 | 6`,elements:[{name:`literal`,value:`1`},{name:`literal`,value:`2`},{name:`literal`,value:`3`},{name:`literal`,value:`4`},{name:`literal`,value:`5`},{name:`literal`,value:`6`}]},{name:`union`,raw:`'empty' | 'in-progress' | 'complete' | 'error'`,elements:[{name:`literal`,value:`'empty'`},{name:`literal`,value:`'in-progress'`},{name:`literal`,value:`'complete'`},{name:`literal`,value:`'error'`}]}],raw:`Record<QuoteIntakeStepId, StepStatus>`},description:``},onStepSelect:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(step: QuoteIntakeStepId) => void`,signature:{arguments:[{type:{name:`union`,raw:`1 | 2 | 3 | 4 | 5 | 6`,elements:[{name:`literal`,value:`1`},{name:`literal`,value:`2`},{name:`literal`,value:`3`},{name:`literal`,value:`4`},{name:`literal`,value:`5`},{name:`literal`,value:`6`}]},name:`step`}],return:{name:`void`}}},description:``}}}}));function f(){return(0,x.jsx)(s,{metadataState:C})}function p(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,quoteIntent:`Purchase`,channel:`Retail`}})}function m(){return(0,x.jsx)(s,{metadataState:C})}function h(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,quoteIntent:`Purchase`,channel:`Retail`,borrowerName:`Alex Borrower`,contactEmail:`alex@example.test`}})}function g(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,loanAmount:`425000`}})}function _(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,propertyState:`CA`,propertyZip:`90001`}})}function v(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,monthlyIncome:`12000`,monthlyDebt:`2500`}})}function y(){return(0,x.jsx)(s,{metadataState:C,intake:{...i,productFamily:`Configured product family`,effectiveDate:`2026-06-11`}})}function b(){return(0,x.jsx)(c,{steps:r,activeStep:3,statuses:{1:`complete`,2:`complete`,3:`in-progress`,4:`empty`,5:`error`,6:`empty`},onStepSelect:()=>void 0})}var x,S,C,w;e((()=>{a(),o(),d(),x=t(),S={title:`PII-25/Quote Intake/Progressive Flow`},C={kind:`loaded`,metadata:n()},f.__docgenInfo={description:``,methods:[],displayName:`FullFlowEmpty`},p.__docgenInfo={description:``,methods:[],displayName:`ResumeDraftScenario`},m.__docgenInfo={description:``,methods:[],displayName:`Step1ScenarioIdentityEmpty`},h.__docgenInfo={description:``,methods:[],displayName:`Step2BorrowerCreditValid`},g.__docgenInfo={description:``,methods:[],displayName:`Step3LoanStructureValid`},_.__docgenInfo={description:``,methods:[],displayName:`Step4PropertyValid`},v.__docgenInfo={description:``,methods:[],displayName:`Step5IncomeAssetsValid`},y.__docgenInfo={description:``,methods:[],displayName:`Step6PreferencesReady`},b.__docgenInfo={description:``,methods:[],displayName:`ProgressIndicatorStates`},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`function FullFlowEmpty() {
  return <QuoteIntakeFlow metadataState={metadataState} />;
}`,...f.parameters?.docs?.source}}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`function ResumeDraftScenario() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    quoteIntent: 'Purchase',
    channel: 'Retail'
  }} />;
}`,...p.parameters?.docs?.source}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`function Step1ScenarioIdentityEmpty() {
  return <QuoteIntakeFlow metadataState={metadataState} />;
}`,...m.parameters?.docs?.source}}},h.parameters={...h.parameters,docs:{...h.parameters?.docs,source:{originalSource:`function Step2BorrowerCreditValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    quoteIntent: 'Purchase',
    channel: 'Retail',
    borrowerName: 'Alex Borrower',
    contactEmail: 'alex@example.test'
  }} />;
}`,...h.parameters?.docs?.source}}},g.parameters={...g.parameters,docs:{...g.parameters?.docs,source:{originalSource:`function Step3LoanStructureValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    loanAmount: '425000'
  }} />;
}`,...g.parameters?.docs?.source}}},_.parameters={..._.parameters,docs:{..._.parameters?.docs,source:{originalSource:`function Step4PropertyValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    propertyState: 'CA',
    propertyZip: '90001'
  }} />;
}`,..._.parameters?.docs?.source}}},v.parameters={...v.parameters,docs:{...v.parameters?.docs,source:{originalSource:`function Step5IncomeAssetsValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    monthlyIncome: '12000',
    monthlyDebt: '2500'
  }} />;
}`,...v.parameters?.docs?.source}}},y.parameters={...y.parameters,docs:{...y.parameters?.docs,source:{originalSource:`function Step6PreferencesReady() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    productFamily: 'Configured product family',
    effectiveDate: '2026-06-11'
  }} />;
}`,...y.parameters?.docs?.source}}},b.parameters={...b.parameters,docs:{...b.parameters?.docs,source:{originalSource:`function ProgressIndicatorStates() {
  return <ProgressIndicator steps={quoteIntakeSteps} activeStep={3} statuses={{
    1: 'complete',
    2: 'complete',
    3: 'in-progress',
    4: 'empty',
    5: 'error',
    6: 'empty'
  }} onStepSelect={() => undefined} />;
}`,...b.parameters?.docs?.source}}},w=[`FullFlowEmpty`,`ResumeDraftScenario`,`Step1ScenarioIdentityEmpty`,`Step2BorrowerCreditValid`,`Step3LoanStructureValid`,`Step4PropertyValid`,`Step5IncomeAssetsValid`,`Step6PreferencesReady`,`ProgressIndicatorStates`]}))();export{f as FullFlowEmpty,b as ProgressIndicatorStates,p as ResumeDraftScenario,m as Step1ScenarioIdentityEmpty,h as Step2BorrowerCreditValid,g as Step3LoanStructureValid,_ as Step4PropertyValid,v as Step5IncomeAssetsValid,y as Step6PreferencesReady,w as __namedExportsOrder,S as default};