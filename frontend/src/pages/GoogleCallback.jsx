import { useEffect, useRef, useState } from 'react';
import { authApi, tokenStore } from '../api.js';

const REDIRECT_URI = 'http://localhost:5173/auth/callback';

/**
 * GoogleCallback
 *
 * This page is the redirect target after Keycloak completes Google OAuth2.
 * Keycloak appends ?code=... (and optionally ?session_state=...) to the URL.
 *
 * Flow:
 *  1. Read `code` from URL search params
 *  2. POST code + redirectUri to backend /auth/google/callback
 *  3. Backend exchanges code → JWT and syncs user to DB
 *  4. Store access token → call onLoginSuccess to transition to the app
 */
export default function GoogleCallback({ onLoginSuccess }) {
  const [status, setStatus] = useState('loading'); // 'loading' | 'error'
  const [errorMsg, setErrorMsg] = useState('');
  const handled = useRef(false); // prevent double-execution in React Strict Mode

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');

    if (!code) {
      setStatus('error');
      setErrorMsg('No authorization code found in URL. Please try logging in again.');
      return;
    }

    authApi
      .googleCallback(code, REDIRECT_URI)
      .then((res) => {
        const { access_token: accessToken, token_type: tokenType, expires_in: expiresIn } = res.data;
        tokenStore.set(accessToken);
        onLoginSuccess({ accessToken, tokenType, expiresIn, username: 'Google User' });
      })
      .catch((err) => {
        console.error('Google callback error:', err);
        setStatus('error');
        setErrorMsg(err.message || 'Google login failed. Please try again.');
      });
  }, [onLoginSuccess]);

  // ─── Loading state ───────────────────────────────────────────────
  if (status === 'loading') {
    return (
      <div className="auth-wrap">
        <div className="card" style={{ textAlign: 'center' }}>
          <div className="card-header">
            <div className="card-icon" style={{ fontSize: '2.5rem' }}>🔄</div>
            <h1 className="card-title">Completing Google Sign-In</h1>
            <p className="card-sub">Please wait while we verify your account…</p>
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: '1.5rem' }}>
            <span className="spinner" style={{ width: '2rem', height: '2rem' }} />
          </div>
        </div>
      </div>
    );
  }

  // ─── Error state ─────────────────────────────────────────────────
  return (
    <div className="auth-wrap">
      <div className="card">
        <div className="card-header">
          <div className="card-icon" style={{ fontSize: '2.5rem' }}>❌</div>
          <h1 className="card-title">Sign-In Failed</h1>
        </div>
        <div className="alert alert-error" style={{ margin: '1rem 0' }}>
          <span>⚠</span> {errorMsg}
        </div>
        <button
          className="btn btn-primary"
          onClick={() => window.location.replace('/')}
          style={{ marginTop: '0.5rem' }}
        >
          Back to Login
        </button>
      </div>
    </div>
  );
}
