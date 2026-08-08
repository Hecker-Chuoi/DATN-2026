import { useState } from 'react';
import { authApi } from '../api.js';

/**
 * RegisterPage
 * POST via Gateway → identity-service → Keycloak (create user)
 * Path: /api/identity/v1/auth/register  (public – no JWT required)
 *
 * Body: { username, password, email, firstName, lastName }
 * → ApiResponse<UserResponse>
 */
export default function RegisterPage({ onGoLogin }) {
  const [form, setForm] = useState({
    username: '', password: '', email: '', firstName: '', lastName: '',
  });
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');
  const [success, setSuccess] = useState('');

  const handleChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      // ApiResponse<UserResponse>
      const res = await authApi.register(form);
      setSuccess(`Account "${res.data.username}" created successfully! You can now sign in.`);
      setForm({ username: '', password: '', email: '', firstName: '', lastName: '' });
    } catch (err) {
      if (err.status === 409) {
        setError('Username or email already exists.');
      } else if (err.status === 400) {
        setError(err.message || 'Validation failed. Check your inputs.');
      } else {
        setError(err.message || 'Registration failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-wrap">
      <div className="card" style={{ maxWidth: 520 }}>
        <div className="card-header">
          <div className="card-icon">✨</div>
          <h1 className="card-title">Create account</h1>
          <p className="card-sub">Join DATN platform today</p>
        </div>

        {error   && <div className="alert alert-error"   style={{ marginBottom: '1rem' }}><span>⚠</span> {error}</div>}
        {success && <div className="alert alert-success" style={{ marginBottom: '1rem' }}><span>✓</span> {success}</div>}

        <form className="form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="reg-username">Username <span style={{color:'var(--error)'}}>*</span></label>
            <input
              id="reg-username" name="username" type="text"
              autoComplete="username"
              placeholder="min 3 characters"
              value={form.username} onChange={handleChange}
              minLength={3} maxLength={50} required
            />
          </div>

          <div className="field">
            <label htmlFor="reg-email">Email <span style={{color:'var(--error)'}}>*</span></label>
            <input
              id="reg-email" name="email" type="email"
              autoComplete="email"
              placeholder="you@example.com"
              value={form.email} onChange={handleChange}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="reg-password">Password <span style={{color:'var(--error)'}}>*</span></label>
            <input
              id="reg-password" name="password" type="password"
              autoComplete="new-password"
              placeholder="min 6 characters"
              value={form.password} onChange={handleChange}
              minLength={6} required
            />
          </div>

          <div className="form-row">
            <div className="field">
              <label htmlFor="reg-firstName">First name</label>
              <input
                id="reg-firstName" name="firstName" type="text"
                placeholder="John"
                value={form.firstName} onChange={handleChange}
                maxLength={100}
              />
            </div>
            <div className="field">
              <label htmlFor="reg-lastName">Last name</label>
              <input
                id="reg-lastName" name="lastName" type="text"
                placeholder="Doe"
                value={form.lastName} onChange={handleChange}
                maxLength={100}
              />
            </div>
          </div>

          <button className="btn btn-primary" type="submit" disabled={loading} style={{ marginTop: '0.5rem' }}>
            {loading ? <span className="spinner" /> : null}
            {loading ? 'Creating account…' : 'Create Account'}
          </button>
        </form>

        <div className="divider">or</div>
        <p style={{ textAlign: 'center', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
          Already have an account?{' '}
          <button className="link-btn" onClick={onGoLogin}>Sign in</button>
        </p>

        <div style={{ marginTop: '1.5rem' }}>
          <div className="token-bar">
            <span style={{ color: 'var(--text-muted)' }}>POST</span>
            <span>Gateway :8080 → identity-service :8082 → Keycloak :8088</span>
          </div>
        </div>
      </div>
    </div>
  );
}
