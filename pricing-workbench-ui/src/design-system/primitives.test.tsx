import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import {
  Accordion,
  Avatar,
  Badge,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  Divider,
  Drawer,
  Dropdown,
  EmptyState,
  FieldGroup,
  Flex,
  Grid,
  Heading,
  Icon,
  Input,
  Label,
  Modal,
  Popover,
  Progress,
  Radio,
  RoleBadge,
  Section,
  Select,
  Skeleton,
  Spinner,
  Stepper,
  Switch,
  Table,
  TabList,
  TabPanel,
  Tabs,
  Text,
  Textarea,
  Tooltip,
  VisuallyHidden,
} from './primitives';

describe('PrimitiveVariantsTest', () => {
  it('buttonHasAllVariantSizeStateCombinations', () => {
    render(<Button variant="danger" size="lg" state="focus">Escalate</Button>);
    const button = screen.getByRole('button', { name: 'Escalate' });
    expect(button).toHaveClass('ds-button', 'ds-variant-danger', 'ds-size-lg', 'ds-state-focus');
  });

  it('inputShowsErrorState', () => {
    render(<Input state="error" aria-label="Loan amount" />);
    expect(screen.getByLabelText('Loan amount')).toHaveAttribute('aria-invalid', 'true');
  });

  it('rendersRequiredPrimitiveSet', () => {
    render(
      <Box>
        <Flex><Grid><Text>Body</Text><Heading>Heading</Heading></Grid></Flex>
        <Label htmlFor="select">Select</Label>
        <Select id="select" variant="glass"><Select.Option>One</Select.Option></Select>
        <Textarea aria-label="Notes" variant="glass" />
        <Checkbox aria-label="Check" variant="glass" />
        <Radio aria-label="Radio" variant="glass" />
        <Switch aria-label="Switch" checked variant="glass" />
        <Card variant="glass">Card</Card>
        <Table variant="glass"><tbody><tr><td>Cell</td></tr></tbody></Table>
        <Tabs variant="glass"><Tabs.List /><Tabs.Trigger active>Tab</Tabs.Trigger><Tabs.Panel>Panel</Tabs.Panel></Tabs>
        <Accordion><summary>Open</summary>Detail</Accordion>
        <Modal title="Modal" variant="glass"><Modal.Header>Head</Modal.Header><Modal.Body><Button>Close</Button></Modal.Body><Modal.Footer>Foot</Modal.Footer></Modal>
        <Drawer variant="glass"><Drawer.Header>Head</Drawer.Header><Drawer.Body>Drawer</Drawer.Body><Drawer.Footer>Foot</Drawer.Footer></Drawer>
        <Tooltip variant="glass">Tooltip</Tooltip>
        <Popover variant="glass">Popover</Popover>
        <Dropdown variant="glass">Dropdown</Dropdown>
        <Avatar initials="WC" />
        <Badge variant="glass" dot>Badge</Badge>
        <Chip variant="glass" removable>Chip</Chip>
        <Icon data-testid="icon" />
        <Spinner />
        <Progress value={50} />
        <Skeleton data-testid="skeleton" variant="glass" />
        <Stepper variant="glass"><Stepper.Step status="current" index={1}>Step one</Stepper.Step><Stepper.Connector /><Stepper.Step index={2}>Step two</Stepper.Step></Stepper>
        <FieldGroup label="Amount" help="Use dollars"><Input aria-label="Amount" /></FieldGroup>
        <Section title="Section" variant="glass">Section body</Section>
        <EmptyState title="Empty" message="Nothing here" />
        <RoleBadge role="loan-officer" />
        <Divider />
        <VisuallyHidden>Hidden helper text</VisuallyHidden>
      </Box>,
    );

    expect(screen.getByRole('dialog', { name: 'Modal' })).toBeInTheDocument();
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50');
    expect(screen.getByText('loan officer')).toHaveStyle({ '--ds-role-badge-bg': '#2dd4bf' });
    expect(screen.getByTestId('icon')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('Hidden helper text')).toHaveClass('ds-visually-hidden');
  });
});
