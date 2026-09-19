import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { AuthForm, Field } from './AuthForm';

const RegisterPage = () => {
  const { register, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/account-dashboard';

  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register(email, password, displayName);
      await login(email, password); // registration does not create a session by itself
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'EMAIL_TAKEN') {
        setFieldErrors({ email: 'An account with this email already exists.' });
      } else if (err instanceof ApiError && err.code === 'VALIDATION_FAILED') {
        setFieldErrors(err.fields);
        setError('Please correct the highlighted fields.');
      } else {
        setError('Registration failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthForm
      title="Create your account"
      subtitle="Demo account: you will receive a clearly labelled synthetic starter history."
      error={error}
      submitting={submitting}
      submitLabel="Create account"
      onSubmit={onSubmit}
      footer={
        <>
          Already registered?{' '}
          <Link to="/login" state={location.state} className="font-medium text-primary hover:text-primary-600">
            Sign in
          </Link>
        </>
      }
    >
      <Field id="displayName" label="Name" type="text" value={displayName} onChange={setDisplayName} autoComplete="name" error={fieldErrors.displayName} />
      <Field id="email" label="Email" type="email" value={email} onChange={setEmail} autoComplete="email" error={fieldErrors.email} />
      <Field
        id="password"
        label="Password"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="new-password"
        error={fieldErrors.password}
        hint="8 to 72 characters."
      />
    </AuthForm>
  );
};

export default RegisterPage;
