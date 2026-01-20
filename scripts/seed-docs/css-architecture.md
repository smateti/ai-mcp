# CSS Architecture Guidelines

## Methodology

### BEM (Block Element Modifier)
- Block: standalone component (.card)
- Element: part of block (.card__title, .card__content)
- Modifier: variation (.card--featured, .card__title--large)

## CSS-in-JS (Styled Components)

### Basic Usage
Create styled components with template literals. Support dynamic props and pseudo-selectors.

### Theming
Define theme object with colors, spacing, typography. Wrap app with ThemeProvider.

## Design Tokens

### Color System
Define CSS custom properties:
- Primary palette (50-900)
- Neutral palette (100-900)
- Semantic colors (success, warning, error)

### Typography
- Font families (base, mono)
- Font sizes (sm, base, lg, xl)
- Line heights (tight, normal, relaxed)

### Spacing Scale
Consistent spacing values:
- spacing-1: 4px
- spacing-2: 8px
- spacing-4: 16px
- spacing-8: 32px

## Responsive Design

### Breakpoints
Mobile-first approach:
- Default: mobile
- 640px: small tablets
- 768px: tablets
- 1024px: desktop
- 1280px: large desktop

## Accessibility
- Use sufficient color contrast (4.5:1 minimum)
- Don't rely solely on color for information
- Ensure focus states are visible
- Support reduced motion preferences
