import{i as e}from"./preload-helper-xPQekRTU.js";import{t}from"./jsx-runtime-CaZkqeYb.js";import{TenantOnboardingScreen as n,t as r}from"./TenantOnboardingScreen-CgdXw6iY.js";function i(){return(0,s.jsx)(s.Fragment,{children:l.map(e=>(0,s.jsx)(`div`,{style:{marginBottom:24},children:(0,s.jsx)(n,{visualState:e})},e))})}function a(){return(0,s.jsx)(n,{})}function o(){return(0,s.jsx)(`div`,{style:{maxWidth:390},children:(0,s.jsx)(n,{})})}var s,c,l,u;e((()=>{r(),s=t(),c={title:`PII-25/Functionality Pages/Tenant Onboarding`},l=[`loading`,`empty`,`blocked`,`needs-attention`,`ready`],i.__docgenInfo={description:``,methods:[],displayName:`AllVisualStates`},a.__docgenInfo={description:``,methods:[],displayName:`DesktopDark`},o.__docgenInfo={description:``,methods:[],displayName:`MobileResponsive`},i.parameters={...i.parameters,docs:{...i.parameters?.docs,source:{originalSource:`function AllVisualStates() {
  return <>{states.map(state => <div key={state} style={{
      marginBottom: 24
    }}><TenantOnboardingScreen visualState={state} /></div>)}</>;
}`,...i.parameters?.docs?.source}}},a.parameters={...a.parameters,docs:{...a.parameters?.docs,source:{originalSource:`function DesktopDark() {
  return <TenantOnboardingScreen />;
}`,...a.parameters?.docs?.source}}},o.parameters={...o.parameters,docs:{...o.parameters?.docs,source:{originalSource:`function MobileResponsive() {
  return <div style={{
    maxWidth: 390
  }}><TenantOnboardingScreen /></div>;
}`,...o.parameters?.docs?.source}}},u=[`AllVisualStates`,`DesktopDark`,`MobileResponsive`]}))();export{i as AllVisualStates,a as DesktopDark,o as MobileResponsive,u as __namedExportsOrder,c as default};