import { Badge, BlockedState, Button, Card, DataTable, EmptyState, FieldGroup, Input, NeedsAttentionState, Progress, RoleBadge, Section, Select, Stepper, Tabs } from './primitives';

export default {
  title: 'Design System/Primitives',
  parameters: { controls: { expanded: true }, visualRegression: { baseline: 'PII-25-S07-glass-primitives' } },
  argTypes: {
    variant: { control: 'select', options: ['default', 'glass'] },
  },
};

export function GlassyShowcase() {
  return (
    <Section title="Glassy primitives" variant="glass">
      <Card variant="glass">
        <FieldGroup label="Pricing scenario" help="Token-backed glass input">
          <Input variant="glass" defaultValue="Conforming purchase" />
        </FieldGroup>
        <Select variant="glass" defaultValue="loan-officer" aria-label="Role">
          <Select.Option value="loan-officer">Loan officer</Select.Option>
          <Select.Option value="pricing-analyst">Pricing analyst</Select.Option>
        </Select>
        <Button variant="glass">Glass action</Button>
        <Badge variant="glass" dot>Live</Badge>
        <RoleBadge role="loan-officer" />
        <Progress value={65} variant="glass" />
      </Card>
    </Section>
  );
}

export function WorkflowStepper() {
  return (
    <Stepper variant="glass">
      <Stepper.Step status="complete" index={1}>Intake</Stepper.Step>
      <Stepper.Connector />
      <Stepper.Step status="current" index={2}>Pricing</Stepper.Step>
      <Stepper.Connector />
      <Stepper.Step index={3}>Review</Stepper.Step>
    </Stepper>
  );
}

export function CompoundTabs() {
  return (
    <Tabs variant="glass">
      <Tabs.List variant="glass"><Tabs.Trigger active>Summary</Tabs.Trigger><Tabs.Trigger>Evidence</Tabs.Trigger></Tabs.List>
      <Tabs.Panel variant="glass">Panel content</Tabs.Panel>
    </Tabs>
  );
}

export function DataAndStates() {
  const columns = [{ key: 'role', header: 'Role', sortable: true }, { key: 'status', header: 'Status' }];
  const rows = [{ role: 'Loan officer', status: <RoleBadge role="loan-officer" /> }, { role: 'Compliance officer', status: <RoleBadge role="compliance-officer" /> }];
  return (
    <Section title="Data table and states" variant="glass">
      <DataTable columns={columns} rows={rows} variant="glass" />
      <EmptyState title="No scenarios" message="Create a scenario to begin." variant="glass" />
      <BlockedState message="Configuration is required before pricing can run." />
      <NeedsAttentionState guidance="Connect a role-aware reviewer before publishing." />
    </Section>
  );
}
