type VariantValue = string | undefined | null | false;

export function cx(...values: VariantValue[]) {
  return values.filter(Boolean).join(' ');
}

export function variantClass(base: string, variants: Record<string, string | undefined>) {
  return cx(base, ...Object.values(variants));
}
