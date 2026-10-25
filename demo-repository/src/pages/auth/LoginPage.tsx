import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { AuthForm, Field } from './AuthForm';

/** Seeded personas from docs/DEMO_SCENARIOS.md; one click fills the form. */
const DEMO_PASSWORD = 'rocket-demo-123';
const DEMO_ACCOUNTS = [['Avery · approved', 'avery@demo.rocket.local'], ['Riley · review', 'riley@demo.rocket.local'], ['Drew · rejected', 'drew@demo.rocket.local'], ['Casey · no budget', 'casey@demo.rocket.local']];

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
      subtitle="Welcome back."
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
          <div className="mt-5 border-t border-gray-100 pt-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Demo accounts</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {DEMO_ACCOUNTS.map(([name, address]) => <button key={address} type="button" className="min-h-11 rounded-full border border-gray-300 px-3 text-sm hover:border-primary"
                onClick={() => { setEmail(address); setPassword(DEMO_PASSWORD); }}>{name}</button>)}
            </div>
          </div>
        </>
      }
    >
      <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
      <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
    </AuthForm>
  );
};

export default LoginPage;
