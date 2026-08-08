import { useState } from 'react';
import { authApi, tokenStore } from '../api.js';

const REDIRECT_URI = 'http://localhost:5173/auth/callback';

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#f5f5f4',
    fontFamily: '"Inter", system-ui, -apple-system, sans-serif',
    padding: '1rem',
  },
  card: {
    width: '100%',
    maxWidth: '380px',
    background: '#ffffff',
    border: '1px solid #e7e5e4',
    borderRadius: '8px',
    padding: '2rem',
    boxShadow: '0 1px 4px rgba(0,0,0,0.07)',
  },
  heading: {
    margin: '0 0 0.25rem',
    fontSize: '1.375rem',
    fontWeight: '600',
    color: '#1c1917',
    letterSpacing: '-0.01em',
  },
  subtext: {
    margin: '0 0 1.75rem',
    fontSize: '0.875rem',
    color: '#78716c',
  },
  label: {
    display: 'block',
    marginBottom: '0.375rem',
    fontSize: '0.8125rem',
    fontWeight: '500',
    color: '#44403c',
  },
  input: {
    width: '100%',
    padding: '0.5rem 0.75rem',
    fontSize: '0.9375rem',
    color: '#1c1917',
    background: '#fafaf9',
    border: '1px solid #d6d3d1',
    borderRadius: '6px',
    outline: 'none',
    boxSizing: 'border-box',
    transition: 'border-color 0.15s',
    fontFamily: 'inherit',
  },
  inputFocus: {
    borderColor: '#2563eb',
    background: '#fff',
  },
  fieldGroup: {
    marginBottom: '1rem',
  },
  primaryBtn: {
    width: '100%',
    padding: '0.5625rem 1rem',
    marginTop: '0.5rem',
    fontSize: '0.9375rem',
    fontWeight: '500',
    color: '#fff',
    background: '#2563eb',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
    transition: 'background 0.15s',
    fontFamily: 'inherit',
  },
  primaryBtnHover: {
    background: '#1d4ed8',
  },
  primaryBtnDisabled: {
    background: '#93c5fd',
    cursor: 'not-allowed',
  },
  divider: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    margin: '1.25rem 0',
    fontSize: '0.8125rem',
    color: '#a8a29e',
  },
  dividerLine: {
    flex: 1,
    height: '1px',
    background: '#e7e5e4',
  },
  googleBtn: {
    width: '100%',
    padding: '0.5625rem 1rem',
    fontSize: '0.9375rem',
    fontWeight: '500',
    color: '#1c1917',
    background: '#fff',
    border: '1px solid #d6d3d1',
    borderRadius: '6px',
    cursor: 'pointer',
    transition: 'border-color 0.15s, background 0.15s',
    fontFamily: 'inherit',
  },
  googleBtnHover: {
    borderColor: '#a8a29e',
    background: '#fafaf9',
  },
  googleBtnDisabled: {
    background: '#fafaf9',
    color: '#a8a29e',
    cursor: 'not-allowed',
  },
  errorBox: {
    padding: '0.625rem 0.875rem',
    marginBottom: '1rem',
    fontSize: '0.875rem',
    color: '#b91c1c',
    background: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: '6px',
  },
  footer: {
    marginTop: '1.25rem',
    textAlign: 'center',
    fontSize: '0.8125rem',
    color: '#78716c',
  },
  footerLink: {
    background: 'none',
    border: 'none',
    padding: 0,
    color: '#2563eb',
    cursor: 'pointer',
    fontSize: 'inherit',
    fontFamily: 'inherit',
    textDecoration: 'underline',
    textUnderlineOffset: '2px',
  },
  spinner: {
    display: 'inline-block',
    width: '14px',
    height: '14px',
    border: '2px solid rgba(255,255,255,0.3)',
    borderTopColor: '#fff',
    borderRadius: '50%',
    animation: 'spin 0.6s linear infinite',
    marginRight: '0.5rem',
    verticalAlign: 'middle',
  },
  spinnerDark: {
    borderColor: 'rgba(0,0,0,0.15)',
    borderTopColor: '#44403c',
  },
};

function Input({ id, type = 'text', placeholder, value, onChange, required, autoComplete }) {
  const [focused, setFocused] = useState(false);
  return (
    <input
      id={id}
      name={id}
      type={type}
      placeholder={placeholder}
      value={value}
      onChange={onChange}
      required={required}
      autoComplete={autoComplete}
      style={{ ...styles.input, ...(focused ? styles.inputFocus : {}) }}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
    />
  );
}

export default function LoginPage({ onLoginSuccess, onGoRegister }) {
  const [form, setForm]               = useState({ username: '', password: '' });
  const [loading, setLoading]         = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError]             = useState('');

  const anyLoading = loading || googleLoading;

  const handleChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (anyLoading) return;
    setError('');
    setLoading(true);
    try {
      const res = await authApi.login(form.username, form.password);
      const { access_token: accessToken, token_type: tokenType, expires_in: expiresIn } = res.data;
      tokenStore.set(accessToken);
      onLoginSuccess({ accessToken, tokenType, expiresIn, username: form.username });
    } catch (err) {
      if (err.status === 401 || err.status === 400 || err.status === 502) {
        setError('Incorrect username or password.');
      } else {
        setError(err.message || 'Login failed. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    if (anyLoading) return;
    setError('');
    setGoogleLoading(true);
    try {
      const res = await authApi.googleInit(REDIRECT_URI);
      const { authorizationUrl } = res.data;
      window.location.href = authorizationUrl;
    } catch (err) {
      setGoogleLoading(false);
      setError(err.message || 'Could not initiate Google login.');
    }
  };

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&display=swap');
        @keyframes spin { to { transform: rotate(360deg); } }
        * { box-sizing: border-box; }
      `}</style>

      <div style={styles.page}>
        <div style={styles.card}>
          {/* Header */}
          <h1 style={styles.heading}>Sign in</h1>
          <p style={styles.subtext}>DATN Developer Portal</p>

          {/* Error */}
          {error && <div style={styles.errorBox}>{error}</div>}

          {/* Username / Password form */}
          <form onSubmit={handleSubmit}>
            <div style={styles.fieldGroup}>
              <label htmlFor="username" style={styles.label}>Username</label>
              <Input
                id="username"
                type="text"
                placeholder="your-username"
                value={form.username}
                onChange={handleChange}
                required
                autoComplete="username"
              />
            </div>

            <div style={styles.fieldGroup}>
              <label htmlFor="password" style={styles.label}>Password</label>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={form.password}
                onChange={handleChange}
                required
                autoComplete="current-password"
              />
            </div>

            <PrimaryButton
              type="submit"
              disabled={anyLoading}
              loading={loading}
              label="Sign In"
              loadingLabel="Signing in..."
            />
          </form>

          {/* Divider */}
          <div style={styles.divider}>
            <div style={styles.dividerLine} />
            or
            <div style={styles.dividerLine} />
          </div>

          {/* Google */}
          <GoogleButton
            onClick={handleGoogleLogin}
            disabled={anyLoading}
            loading={googleLoading}
          />

          {/* Register link */}
          <div style={styles.footer}>
            Don't have an account?{' '}
            <button style={styles.footerLink} onClick={onGoRegister} type="button">
              Create one
            </button>
          </div>
        </div>
      </div>
    </>
  );
}

/* ── Sub-components ────────────────────────────────────────── */

function PrimaryButton({ type, disabled, loading, label, loadingLabel }) {
  const [hovered, setHovered] = useState(false);
  const s = {
    ...styles.primaryBtn,
    ...(hovered && !disabled ? styles.primaryBtnHover : {}),
    ...(disabled ? styles.primaryBtnDisabled : {}),
  };
  return (
    <button
      type={type}
      style={s}
      disabled={disabled}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {loading && <span style={styles.spinner} />}
      {loading ? loadingLabel : label}
    </button>
  );
}

function GoogleButton({ onClick, disabled, loading }) {
  const [hovered, setHovered] = useState(false);
  const s = {
    ...styles.googleBtn,
    ...(hovered && !disabled ? styles.googleBtnHover : {}),
    ...(disabled ? styles.googleBtnDisabled : {}),
  };
  return (
    <button
      id="btn-google-login"
      type="button"
      style={s}
      disabled={disabled}
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {loading && <span style={{ ...styles.spinner, ...styles.spinnerDark }} />}
      {loading ? 'Redirecting to Google...' : 'Continue with Google'}
    </button>
  );
}
