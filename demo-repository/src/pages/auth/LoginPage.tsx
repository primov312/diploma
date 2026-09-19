import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { AuthForm, Field } from './AuthForm';

const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/account-dashboard';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      // The backend answers the same way for a wrong password and an unknown email.
      setError(err instanceof ApiError && err.status === 401 ? 'Invalid email or password.' : 'Sign in failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthForm
      title="Sign in"
      subtitle="Use your Rocket Credit account to see your history and apply for financing."
      error={error}
      submitting={submitting}
      submitLabel="Sign in"
      onSubmit={onSubmit}
      footer={
        <>
          New here?{' '}
          <Link to="/register" state={location.state} className="font-medium text-primary hover:text-primary-600">
            Create an account
          </Link>
        </>
      }
    >
      <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
      <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
    </AuthForm>
  );
};

export default LoginPage;
