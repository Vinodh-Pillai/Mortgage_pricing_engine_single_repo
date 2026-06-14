import{i as e}from"./preload-helper-xPQekRTU.js";import{t}from"./jsx-runtime-CaZkqeYb.js";import{a as n,c as r,i,l as a,n as o,o as s,r as c,t as l}from"./QuoteIntakeFlow-CvyOEaSP.js";function u(){return(0,y.jsx)(l,{metadataState:x})}function d(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,quoteIntent:`Purchase`,channel:`Retail`}})}function f(){return(0,y.jsx)(l,{metadataState:x})}function p(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,quoteIntent:`Purchase`,channel:`Retail`,borrowerName:`Alex Borrower`,contactEmail:`alex@example.test`}})}function m(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,loanAmount:`425000`}})}function h(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,propertyState:`CA`,propertyZip:`90001`}})}function g(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,monthlyIncome:`12000`,monthlyDebt:`2500`}})}function _(){return(0,y.jsx)(l,{metadataState:x,intake:{...c,productFamily:`Configured product family`,effectiveDate:`2026-06-11`}})}function v(){return(0,y.jsx)(i,{steps:a,activeStep:3,statuses:{1:`complete`,2:`complete`,3:`in-progress`,4:`empty`,5:`error`,6:`empty`},onStepSelect:()=>void 0})}var y,b,x,S;e((()=>{o(),r(),n(),y=t(),b={title:`PII-25/Quote Intake/Progressive Flow`},x={kind:`loaded`,metadata:s()},u.__docgenInfo={description:``,methods:[],displayName:`FullFlowEmpty`},d.__docgenInfo={description:``,methods:[],displayName:`ResumeDraftScenario`},f.__docgenInfo={description:``,methods:[],displayName:`Step1ScenarioIdentityEmpty`},p.__docgenInfo={description:``,methods:[],displayName:`Step2BorrowerCreditValid`},m.__docgenInfo={description:``,methods:[],displayName:`Step3LoanStructureValid`},h.__docgenInfo={description:``,methods:[],displayName:`Step4PropertyValid`},g.__docgenInfo={description:``,methods:[],displayName:`Step5IncomeAssetsValid`},_.__docgenInfo={description:``,methods:[],displayName:`Step6PreferencesReady`},v.__docgenInfo={description:``,methods:[],displayName:`ProgressIndicatorStates`},u.parameters={...u.parameters,docs:{...u.parameters?.docs,source:{originalSource:`function FullFlowEmpty() {
  return <QuoteIntakeFlow metadataState={metadataState} />;
}`,...u.parameters?.docs?.source}}},d.parameters={...d.parameters,docs:{...d.parameters?.docs,source:{originalSource:`function ResumeDraftScenario() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    quoteIntent: 'Purchase',
    channel: 'Retail'
  }} />;
}`,...d.parameters?.docs?.source}}},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`function Step1ScenarioIdentityEmpty() {
  return <QuoteIntakeFlow metadataState={metadataState} />;
}`,...f.parameters?.docs?.source}}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`function Step2BorrowerCreditValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    quoteIntent: 'Purchase',
    channel: 'Retail',
    borrowerName: 'Alex Borrower',
    contactEmail: 'alex@example.test'
  }} />;
}`,...p.parameters?.docs?.source}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`function Step3LoanStructureValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    loanAmount: '425000'
  }} />;
}`,...m.parameters?.docs?.source}}},h.parameters={...h.parameters,docs:{...h.parameters?.docs,source:{originalSource:`function Step4PropertyValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    propertyState: 'CA',
    propertyZip: '90001'
  }} />;
}`,...h.parameters?.docs?.source}}},g.parameters={...g.parameters,docs:{...g.parameters?.docs,source:{originalSource:`function Step5IncomeAssetsValid() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    monthlyIncome: '12000',
    monthlyDebt: '2500'
  }} />;
}`,...g.parameters?.docs?.source}}},_.parameters={..._.parameters,docs:{..._.parameters?.docs,source:{originalSource:`function Step6PreferencesReady() {
  return <QuoteIntakeFlow metadataState={metadataState} intake={{
    ...initialQuoteIntake,
    productFamily: 'Configured product family',
    effectiveDate: '2026-06-11'
  }} />;
}`,..._.parameters?.docs?.source}}},v.parameters={...v.parameters,docs:{...v.parameters?.docs,source:{originalSource:`function ProgressIndicatorStates() {
  return <ProgressIndicator steps={quoteIntakeSteps} activeStep={3} statuses={{
    1: 'complete',
    2: 'complete',
    3: 'in-progress',
    4: 'empty',
    5: 'error',
    6: 'empty'
  }} onStepSelect={() => undefined} />;
}`,...v.parameters?.docs?.source}}},S=[`FullFlowEmpty`,`ResumeDraftScenario`,`Step1ScenarioIdentityEmpty`,`Step2BorrowerCreditValid`,`Step3LoanStructureValid`,`Step4PropertyValid`,`Step5IncomeAssetsValid`,`Step6PreferencesReady`,`ProgressIndicatorStates`]}))();export{u as FullFlowEmpty,v as ProgressIndicatorStates,d as ResumeDraftScenario,f as Step1ScenarioIdentityEmpty,p as Step2BorrowerCreditValid,m as Step3LoanStructureValid,h as Step4PropertyValid,g as Step5IncomeAssetsValid,_ as Step6PreferencesReady,S as __namedExportsOrder,b as default};