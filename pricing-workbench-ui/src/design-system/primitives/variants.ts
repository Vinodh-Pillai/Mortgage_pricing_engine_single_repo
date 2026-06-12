type VariantValue = string | undefined | null | false;

export function cx(...values: VariantValue[]) {
  return values.filter(Boolean).join(' ');
}

export function variantClass(base: string, variants: Record<string, string | undefined>) {
  return cx(base, ...Object.values(variants));
}

type CvaConfig<TVariants extends Record<string, Record<string, string>>> = {
  variants: TVariants;
  defaultVariants?: { [K in keyof TVariants]?: keyof TVariants[K] };
};

export function cva<TVariants extends Record<string, Record<string, string>>>(base: string, config: CvaConfig<TVariants>) {
  return (selection: { [K in keyof TVariants]?: keyof TVariants[K] } = {}) => {
    const classes = Object.entries(config.variants).map(([variantName, values]) => {
      const selected = (selection as Record<string, string | undefined>)[variantName] ?? String(config.defaultVariants?.[variantName]);
      return selected ? values[selected] : undefined;
    });
    return cx(base, ...classes);
  };
}
