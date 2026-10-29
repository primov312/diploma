// AI-assisted (Claude), 2026: role-aware redirect and staff demo accounts added; reviewed by the author.
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { homeFor, roleForPath } from '../../auth/roles';
import { AuthForm, Field } from './AuthForm';

/** Seeded personas from docs/DEMO_SCENARIOS.md; one click fills the form. */
const DEMO_PASSWORD = 'rocket-demo-123';
const DEMO_ACCOUNTS = [['Avery · approved', 'avery@demo.rocket.local'], ['Riley · review', 'riley@demo.rocket.local'], ['Drew · rejected', 'drew@demo.rocket.local'], ['Casey · no budget', 'casey@demo.rocket.local']];
const STAFF_ACCOUNTS = [['Netflix seller', 'netflix@demo.rocket.local'], ['Zara seller', 'zara@demo.rocket.local'], ['Amazon seller', 'amazon@demo.rocket.local'], ['Administrator', 'admin@demo.rocket.local']];

const LoginPage = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const me = await login(email, password);
      // Return to the remembered page only if it belongs to this kind of user (or to nobody, like the marketplace).
      const owner = from ? roleForPath(from) : null;
      navigate(from && (owner === null || owner === me.role) ? from : homeFor(me), { replace: true });
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
          {([['Demo accounts', DEMO_ACCOUNTS], ['Demo seller and administrator accounts', STAFF_ACCOUNTS]] as const).map(([label, accounts]) =>
            <div key={label} className="mt-5 border-t border-gray-100 pt-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {accounts.map(([name, address]) => <button key={address} type="button" className="min-h-11 rounded-full border border-gray-300 px-3 text-sm hover:border-primary"
                  onClick={() => { setEmail(address); setPassword(DEMO_PASSWORD); }}>{name}</button>)}
              </div>
            </div>)}
        </>
      }
    >
      <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" />
      <Field id="password" label="Password" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
    </AuthForm>
  );
};

export default LoginPage;
