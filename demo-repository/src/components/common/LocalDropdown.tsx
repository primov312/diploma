import { useEffect, useRef, useState, type KeyboardEvent } from 'react';

export type DropdownOption = {
  value: string;
  label: string;
};

type LocalDropdownProps = {
  options: DropdownOption[];
  value: string;
  onValueChange: (value: string) => void;
  className?: string;
  id?: string;
  name?: string;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  'aria-label'?: string;
  'aria-describedby'?: string;
};

/** App-styled dropdown that keeps a native select in the form for validation and submitted values. */
export default function LocalDropdown({
  options,
  value,
  onValueChange,
  className = '',
  id,
  name,
  placeholder = 'Select an option',
  required = false,
  disabled = false,
  'aria-label': ariaLabel,
  'aria-describedby': ariaDescribedBy,
}: LocalDropdownProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const menuOptions = placeholder ? [{ value: '', label: placeholder }, ...options] : options;
  const selected = options.find((option) => option.value === value);

  useEffect(() => {
    if (!open) return;
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  useEffect(() => {
    if (open) optionRefs.current[activeIndex]?.focus();
  }, [open, activeIndex]);

  const showMenu = () => {
    const index = Math.max(0, menuOptions.findIndex((option) => option.value === value));
    setActiveIndex(index);
    setOpen(true);
  };

  const onButtonKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
      event.preventDefault();
      if (!open) showMenu();
    }
  };

  const onOptionKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => (index + (event.key === 'ArrowDown' ? 1 : -1) + menuOptions.length) % menuOptions.length);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      setActiveIndex(event.key === 'Home' ? 0 : menuOptions.length - 1);
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  };

  const selectOption = (option: DropdownOption) => {
    onValueChange(option.value);
    setOpen(false);
    buttonRef.current?.focus();
  };

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      {name && (
        <select
          className="absolute h-px w-px overflow-hidden opacity-0"
          tabIndex={-1}
          aria-hidden="true"
          name={name}
          value={value}
          required={required}
          onChange={(event) => onValueChange(event.target.value)}
          onInvalid={(event) => {
            event.preventDefault();
            buttonRef.current?.focus();
          }}
        >
          {placeholder && <option value="">{placeholder}</option>}
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      )}
      <button
        ref={buttonRef}
        id={id}
        type="button"
        disabled={disabled}
        aria-label={ariaLabel}
        aria-describedby={ariaDescribedBy}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => open ? setOpen(false) : showMenu()}
        onKeyDown={onButtonKeyDown}
        className="flex min-h-[42px] w-full items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white px-3 py-2 text-left text-sm text-gray-800 shadow-sm transition-colors hover:border-gray-300 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400"
      >
        <span className={selected ? '' : 'text-gray-500'}>{selected?.label ?? placeholder}</span>
        <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" className={`h-4 w-4 shrink-0 text-gray-500 transition-transform ${open ? 'rotate-180' : ''}`}>
          <path d="m5 7.5 5 5 5-5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && (
        <div className="absolute z-50 mt-2 max-h-64 w-full overflow-auto rounded-xl border border-gray-100 bg-white p-1.5 shadow-xl ring-1 ring-black/5" role="listbox" aria-label={ariaLabel}>
          {menuOptions.map((option, index) => {
            const isSelected = option.value === value;
            return (
              <button
                key={option.value}
                ref={(element) => { optionRefs.current[index] = element; }}
                type="button"
                role="option"
                aria-selected={isSelected}
                tabIndex={-1}
                onClick={() => selectOption(option)}
                onKeyDown={onOptionKeyDown}
                onMouseEnter={() => setActiveIndex(index)}
                className={`flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-left text-sm transition-colors ${isSelected ? 'bg-primary-50 font-medium text-primary-700' : 'text-gray-700 hover:bg-gray-50'} focus:bg-primary-50 focus:outline-none`}
              >
                <span>{option.label}</span>
                {isSelected && <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" className="h-4 w-4 text-primary"><path d="m4.5 10 3.5 3.5 7.5-7.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
