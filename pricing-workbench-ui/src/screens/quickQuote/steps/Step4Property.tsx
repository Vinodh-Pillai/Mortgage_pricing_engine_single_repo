import { MortgageInput } from '../../../components/MortgageInput';
import type { BorrowerIntake } from '../../../lib/api/quoteRuns';

export function Step4Property({
  intake,
  errors,
  onChange,
}: {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  return (
    <fieldset className="quick-quote-section">
      <legend>Step 4: Property (PII-01-S04)</legend>
      <p className="field-help">Enter property location, type, occupancy, and value. System validates against geographic catalogs.</p>
      <div className="quick-quote-minimal">
        <MortgageInput
          id="propertyState"
          label="Property state *"
          value={intake.propertyState}
          error={errors.propertyState}
          onChange={onChange}
          as="select"
          options={[
            { value: '', label: 'Select state' },
            { value: 'CA', label: 'California' },
            { value: 'TX', label: 'Texas' },
            { value: 'FL', label: 'Florida' },
            { value: 'NY', label: 'New York' },
          ]}
        />
        <MortgageInput id="propertyCounty" label="Property county" value={intake.propertyCounty} error={errors.propertyCounty} onChange={onChange} placeholder="e.g., Travis" />
        <MortgageInput id="propertyZip" label="Property ZIP *" value={intake.propertyZip} error={errors.propertyZip} onChange={onChange} placeholder="5 or 9 digit ZIP" />
        <MortgageInput
          id="propertyType"
          label="Property type"
          value={intake.propertyType}
          error={errors.propertyType}
          onChange={onChange}
          as="select"
          options={[
            { value: 'SINGLE_FAMILY', label: 'Single Family' },
            { value: 'CONDO', label: 'Condo' },
            { value: 'TOWNHOUSE', label: 'Townhouse' },
            { value: 'MULTI_FAMILY_2_4', label: 'Multi-Family (2-4 units)' },
            { value: 'MANUFACTURED', label: 'Manufactured Home' },
          ]}
        />
        <MortgageInput
          id="occupancyType"
          label="Occupancy type"
          value={intake.occupancyType}
          error={errors.occupancyType}
          onChange={onChange}
          as="select"
          options={[
            { value: 'PRIMARY_RESIDENCE', label: 'Primary Residence' },
            { value: 'SECOND_HOME', label: 'Second Home' },
            { value: 'INVESTMENT_PROPERTY', label: 'Investment Property' },
          ]}
        />
        <MortgageInput id="unitCount" label="Unit count (1-4)" value={intake.unitCount} error={errors.unitCount} type="number" onChange={onChange} />
        <MortgageInput id="purchasePrice" label="Purchase price" value={intake.purchasePrice} error={errors.purchasePrice} type="number" onChange={onChange} />
        <MortgageInput id="appraisedValue" label="Appraised value (optional)" value={intake.appraisedValue} error={errors.appraisedValue} type="number" onChange={onChange} />
        <MortgageInput id="condoProjectType" label="Condo project type (if condo)" value={intake.condoProjectType} error={errors.condoProjectType} onChange={onChange} />
        <MortgageInput
          id="manufacturedHomeFlag"
          label="Manufactured home flag"
          value={intake.manufacturedHomeFlag}
          error={errors.manufacturedHomeFlag}
          onChange={onChange}
          as="select"
          options={[
            { value: 'false', label: 'No' },
            { value: 'true', label: 'Yes' },
          ]}
        />
      </div>
    </fieldset>
  );
}
