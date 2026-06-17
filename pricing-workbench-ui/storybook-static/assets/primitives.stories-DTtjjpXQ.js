import{i as e}from"./preload-helper-xPQekRTU.js";import{_ as t,a as n,b as r,d as i,f as a,h as o,i as s,m as c,n as l,o as u,p as d,r as f,s as p,t as m,u as h,v as g}from"./primitives-COJqNAmI.js";import{t as _}from"./jsx-runtime-CaZkqeYb.js";function v(){return(0,S.jsx)(c,{title:`Glassy primitives`,variant:`glass`,children:(0,S.jsxs)(s,{variant:`glass`,children:[(0,S.jsx)(p,{label:`Pricing scenario`,help:`Token-backed glass input`,children:(0,S.jsx)(h,{variant:`glass`,defaultValue:`Conforming purchase`})}),(0,S.jsxs)(o,{variant:`glass`,defaultValue:`loan-officer`,"aria-label":`Role`,children:[(0,S.jsx)(o.Option,{value:`loan-officer`,children:`Loan officer`}),(0,S.jsx)(o.Option,{value:`pricing-analyst`,children:`Pricing analyst`})]}),(0,S.jsx)(f,{variant:`glass`,children:`Glass action`}),(0,S.jsx)(m,{variant:`glass`,dot:!0,children:`Live`}),(0,S.jsx)(d,{role:`loan-officer`}),(0,S.jsx)(a,{value:65,variant:`glass`})]})})}function y(){return(0,S.jsxs)(t,{variant:`glass`,children:[(0,S.jsx)(t.Step,{status:`complete`,index:1,children:`Intake`}),(0,S.jsx)(t.Connector,{}),(0,S.jsx)(t.Step,{status:`current`,index:2,children:`Pricing`}),(0,S.jsx)(t.Connector,{}),(0,S.jsx)(t.Step,{index:3,children:`Review`})]})}function b(){return(0,S.jsxs)(g,{variant:`glass`,children:[(0,S.jsxs)(g.List,{variant:`glass`,children:[(0,S.jsx)(g.Trigger,{active:!0,children:`Summary`}),(0,S.jsx)(g.Trigger,{children:`Evidence`})]}),(0,S.jsx)(g.Panel,{variant:`glass`,children:`Panel content`})]})}function x(){return(0,S.jsxs)(c,{title:`Data table and states`,variant:`glass`,children:[(0,S.jsx)(n,{columns:[{key:`role`,header:`Role`,sortable:!0},{key:`status`,header:`Status`}],rows:[{role:`Loan officer`,status:(0,S.jsx)(d,{role:`loan-officer`})},{role:`Compliance officer`,status:(0,S.jsx)(d,{role:`compliance-officer`})}],variant:`glass`}),(0,S.jsx)(u,{title:`No scenarios`,message:`Create a scenario to begin.`,variant:`glass`}),(0,S.jsx)(l,{message:`Configuration is required before pricing can run.`}),(0,S.jsx)(i,{guidance:`Connect a role-aware reviewer before publishing.`})]})}var S,C,w;e((()=>{r(),S=_(),C={title:`Design System/Primitives`,parameters:{controls:{expanded:!0},visualRegression:{baseline:`PII-25-S07-glass-primitives`}},argTypes:{variant:{control:`select`,options:[`default`,`glass`]}}},v.__docgenInfo={description:``,methods:[],displayName:`GlassyShowcase`},y.__docgenInfo={description:``,methods:[],displayName:`WorkflowStepper`},b.__docgenInfo={description:``,methods:[],displayName:`CompoundTabs`},x.__docgenInfo={description:``,methods:[],displayName:`DataAndStates`},v.parameters={...v.parameters,docs:{...v.parameters?.docs,source:{originalSource:`function GlassyShowcase() {
  return <Section title="Glassy primitives" variant="glass">\r
      <Card variant="glass">\r
        <FieldGroup label="Pricing scenario" help="Token-backed glass input">\r
          <Input variant="glass" defaultValue="Conforming purchase" />\r
        </FieldGroup>\r
        <Select variant="glass" defaultValue="loan-officer" aria-label="Role">\r
          <Select.Option value="loan-officer">Loan officer</Select.Option>\r
          <Select.Option value="pricing-analyst">Pricing analyst</Select.Option>\r
        </Select>\r
        <Button variant="glass">Glass action</Button>\r
        <Badge variant="glass" dot>Live</Badge>\r
        <RoleBadge role="loan-officer" />\r
        <Progress value={65} variant="glass" />\r
      </Card>\r
    </Section>;
}`,...v.parameters?.docs?.source}}},y.parameters={...y.parameters,docs:{...y.parameters?.docs,source:{originalSource:`function WorkflowStepper() {
  return <Stepper variant="glass">\r
      <Stepper.Step status="complete" index={1}>Intake</Stepper.Step>\r
      <Stepper.Connector />\r
      <Stepper.Step status="current" index={2}>Pricing</Stepper.Step>\r
      <Stepper.Connector />\r
      <Stepper.Step index={3}>Review</Stepper.Step>\r
    </Stepper>;
}`,...y.parameters?.docs?.source}}},b.parameters={...b.parameters,docs:{...b.parameters?.docs,source:{originalSource:`function CompoundTabs() {
  return <Tabs variant="glass">\r
      <Tabs.List variant="glass"><Tabs.Trigger active>Summary</Tabs.Trigger><Tabs.Trigger>Evidence</Tabs.Trigger></Tabs.List>\r
      <Tabs.Panel variant="glass">Panel content</Tabs.Panel>\r
    </Tabs>;
}`,...b.parameters?.docs?.source}}},x.parameters={...x.parameters,docs:{...x.parameters?.docs,source:{originalSource:`function DataAndStates() {
  const columns = [{
    key: 'role',
    header: 'Role',
    sortable: true
  }, {
    key: 'status',
    header: 'Status'
  }];
  const rows = [{
    role: 'Loan officer',
    status: <RoleBadge role="loan-officer" />
  }, {
    role: 'Compliance officer',
    status: <RoleBadge role="compliance-officer" />
  }];
  return <Section title="Data table and states" variant="glass">\r
      <DataTable columns={columns} rows={rows} variant="glass" />\r
      <EmptyState title="No scenarios" message="Create a scenario to begin." variant="glass" />\r
      <BlockedState message="Configuration is required before pricing can run." />\r
      <NeedsAttentionState guidance="Connect a role-aware reviewer before publishing." />\r
    </Section>;
}`,...x.parameters?.docs?.source}}},w=[`GlassyShowcase`,`WorkflowStepper`,`CompoundTabs`,`DataAndStates`]}))();export{b as CompoundTabs,x as DataAndStates,v as GlassyShowcase,y as WorkflowStepper,w as __namedExportsOrder,C as default};