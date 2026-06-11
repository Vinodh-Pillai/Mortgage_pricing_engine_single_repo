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
  Flex,
  Grid,
  Heading,
  Icon,
  Input,
  Label,
  Modal,
  Popover,
  Radio,
  Select,
  Skeleton,
  Spinner,
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
        <Select id="select"><option>One</option></Select>
        <Textarea aria-label="Notes" />
        <Checkbox aria-label="Check" />
        <Radio aria-label="Radio" />
        <Switch aria-label="Switch" checked />
        <Card>Card</Card>
        <Table><tbody><tr><td>Cell</td></tr></tbody></Table>
        <Tabs><TabList /><TabPanel>Panel</TabPanel></Tabs>
        <Accordion><summary>Open</summary>Detail</Accordion>
        <Modal title="Modal"><Button>Close</Button></Modal>
        <Drawer>Drawer</Drawer>
        <Tooltip>Tooltip</Tooltip>
        <Popover>Popover</Popover>
        <Dropdown>Dropdown</Dropdown>
        <Avatar initials="WC" />
        <Badge>Badge</Badge>
        <Chip>Chip</Chip>
        <Icon data-testid="icon" />
        <Spinner />
        <Skeleton data-testid="skeleton" />
        <Divider />
        <VisuallyHidden>Hidden helper text</VisuallyHidden>
      </Box>,
    );

    expect(screen.getByRole('dialog', { name: 'Modal' })).toBeInTheDocument();
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByTestId('icon')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByText('Hidden helper text')).toHaveClass('ds-visually-hidden');
  });
});
