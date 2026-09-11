import type { ReactNode } from "react";

export type StatItem = {
  value: ReactNode;
  label: ReactNode;
  description?: ReactNode;
  key?: string | number;
};

export type StatGridProps = {
  items: StatItem[];
  className?: string;
  cardClassName?: string;
  valueClassName?: string;
  labelClassName?: string;
  descriptionClassName?: string;
};

const StatGrid = ({
  items,
  className = "",
  cardClassName = "text-center",
  valueClassName = "text-3xl font-bold text-primary",
  labelClassName = "text-sm text-gray-600",
  descriptionClassName = "text-xs text-gray-500",
}: StatGridProps) => (
  <div className={className}>
    {items.map(({ value, label, description, key }, index) => (
      <div key={key ?? `${index}-${String(label)}`} className={cardClassName}>
        <div className={valueClassName}>{value}</div>
        <div className={labelClassName}>{label}</div>
        {description ? (
          <div className={descriptionClassName}>{description}</div>
        ) : null}
      </div>
    ))}
  </div>
);

export default StatGrid;
