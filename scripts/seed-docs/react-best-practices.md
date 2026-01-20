# React Development Best Practices

## Component Design

### Functional Components
Always use functional components with hooks:
- useState for local state
- useEffect for side effects
- Custom hooks for reusable logic

### Component Composition
- Keep components small and focused
- Use composition over inheritance
- Extract reusable logic into custom hooks

### Props Best Practices
- Use TypeScript for prop types
- Provide default values
- Destructure props in function signature

## State Management

### Local State
Use useState for:
- Form inputs
- UI state (modals, toggles)
- Component-specific data

### Context
Use Context for:
- Theme settings
- User authentication
- Localization

### Global State (Redux/Zustand)
Use for:
- Shared application state
- Complex state logic
- State that persists across routes

## Performance Optimization

### Memoization
- useMemo for expensive calculations
- useCallback for stable function references
- React.memo for preventing unnecessary re-renders

### Code Splitting
Use React.lazy and Suspense for dynamic imports. Split routes and heavy components.

## Testing

### Component Testing
Use React Testing Library:
- render components
- screen.getByLabelText/getByText for queries
- fireEvent for user interactions
- Assert on expected behavior

### Integration Testing
- Test user flows end-to-end
- Mock API responses
- Test error states
