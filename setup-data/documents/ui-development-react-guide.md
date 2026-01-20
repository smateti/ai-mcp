# React Frontend Development Guide

## Introduction to React
React is a JavaScript library for building user interfaces, developed by Facebook. It uses a component-based architecture and virtual DOM for efficient rendering.

## Setting Up a React Project
```bash
# Using Create React App
npx create-react-app my-app --template typescript

# Using Vite (recommended for new projects)
npm create vite@latest my-app -- --template react-ts
cd my-app
npm install
npm run dev
```

## Component Basics

### Functional Components
```tsx
import React from 'react';

interface UserProps {
  name: string;
  email: string;
  onUpdate?: (name: string) => void;
}

const UserCard: React.FC<UserProps> = ({ name, email, onUpdate }) => {
  return (
    <div className="user-card">
      <h3>{name}</h3>
      <p>{email}</p>
      {onUpdate && (
        <button onClick={() => onUpdate(name)}>Update</button>
      )}
    </div>
  );
};

export default UserCard;
```

### Using Hooks

#### useState for State Management
```tsx
const Counter: React.FC = () => {
  const [count, setCount] = useState(0);
  const [user, setUser] = useState<User | null>(null);

  return (
    <div>
      <p>Count: {count}</p>
      <button onClick={() => setCount(prev => prev + 1)}>Increment</button>
    </div>
  );
};
```

#### useEffect for Side Effects
```tsx
const UserList: React.FC = () => {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const response = await fetch('/api/users');
        const data = await response.json();
        setUsers(data);
      } catch (error) {
        console.error('Failed to fetch users:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchUsers();
  }, []); // Empty dependency array = runs once on mount

  if (loading) return <div>Loading...</div>;

  return (
    <ul>
      {users.map(user => (
        <li key={user.id}>{user.name}</li>
      ))}
    </ul>
  );
};
```

#### Custom Hooks
```tsx
function useApi<T>(url: string) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const response = await fetch(url);
        if (!response.ok) throw new Error('Request failed');
        const result = await response.json();
        setData(result);
      } catch (err) {
        setError(err as Error);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [url]);

  return { data, loading, error };
}

// Usage
const { data: users, loading, error } = useApi<User[]>('/api/users');
```

## State Management with Context
```tsx
// AuthContext.tsx
interface AuthContextType {
  user: User | null;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);

  const login = async (credentials: LoginCredentials) => {
    const response = await fetch('/api/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    });
    const userData = await response.json();
    setUser(userData);
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('token');
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
```

## Routing with React Router
```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="users" element={<UserList />} />
          <Route path="users/:id" element={<UserDetail />} />
          <Route path="login" element={<Login />} />
          <Route
            path="admin"
            element={
              <ProtectedRoute>
                <AdminDashboard />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
};

// Protected Route Component
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};
```

## Form Handling
```tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  email: z.string().email('Invalid email'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

type FormData = z.infer<typeof schema>;

const LoginForm: React.FC = () => {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (data: FormData) => {
    try {
      await login(data);
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <div>
        <input {...register('email')} placeholder="Email" />
        {errors.email && <span className="error">{errors.email.message}</span>}
      </div>
      <div>
        <input {...register('password')} type="password" placeholder="Password" />
        {errors.password && <span className="error">{errors.password.message}</span>}
      </div>
      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Logging in...' : 'Login'}
      </button>
    </form>
  );
};
```

## API Integration with Axios
```tsx
import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// Request interceptor for auth token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export const userService = {
  getAll: () => api.get<User[]>('/users'),
  getById: (id: string) => api.get<User>(`/users/${id}`),
  create: (data: CreateUserDto) => api.post<User>('/users', data),
  update: (id: string, data: UpdateUserDto) => api.put<User>(`/users/${id}`, data),
  delete: (id: string) => api.delete(`/users/${id}`),
};
```

## Styling with CSS Modules or Tailwind
```tsx
// Using CSS Modules
import styles from './Button.module.css';

const Button: React.FC<ButtonProps> = ({ children, variant = 'primary' }) => {
  return (
    <button className={`${styles.button} ${styles[variant]}`}>
      {children}
    </button>
  );
};

// Using Tailwind CSS
const Button: React.FC<ButtonProps> = ({ children, variant = 'primary' }) => {
  const variants = {
    primary: 'bg-blue-500 hover:bg-blue-600 text-white',
    secondary: 'bg-gray-200 hover:bg-gray-300 text-gray-800',
    danger: 'bg-red-500 hover:bg-red-600 text-white',
  };

  return (
    <button className={`px-4 py-2 rounded font-medium ${variants[variant]}`}>
      {children}
    </button>
  );
};
```

## Performance Optimization

### React.memo for Component Memoization
```tsx
const UserCard = React.memo<UserProps>(({ name, email }) => {
  return (
    <div className="user-card">
      <h3>{name}</h3>
      <p>{email}</p>
    </div>
  );
});
```

### useMemo and useCallback
```tsx
const ExpensiveComponent: React.FC<{ items: Item[] }> = ({ items }) => {
  // Memoize expensive calculation
  const sortedItems = useMemo(
    () => items.sort((a, b) => a.name.localeCompare(b.name)),
    [items]
  );

  // Memoize callback function
  const handleClick = useCallback((id: string) => {
    console.log('Clicked item:', id);
  }, []);

  return (
    <ul>
      {sortedItems.map(item => (
        <li key={item.id} onClick={() => handleClick(item.id)}>
          {item.name}
        </li>
      ))}
    </ul>
  );
};
```

## Testing with Jest and React Testing Library
```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

describe('LoginForm', () => {
  it('should display validation errors for invalid input', async () => {
    render(<LoginForm />);

    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    expect(await screen.findByText(/invalid email/i)).toBeInTheDocument();
  });

  it('should call login on valid submission', async () => {
    const mockLogin = jest.fn();
    render(<LoginForm onLogin={mockLogin} />);

    await userEvent.type(screen.getByPlaceholderText(/email/i), 'test@example.com');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'password123');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'password123',
      });
    });
  });
});
```

## Best Practices
1. Keep components small and focused (single responsibility)
2. Use TypeScript for type safety
3. Lift state up only when necessary
4. Use React Query or SWR for server state
5. Implement proper error boundaries
6. Lazy load routes and heavy components
7. Write tests for critical user flows
