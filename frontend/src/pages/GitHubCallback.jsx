import { useEffect, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle, AlertCircle, Loader2 } from 'lucide-react';

export default function GitHubCallback() {
  const [searchParams] = useSearchParams();
  const code = searchParams.get('code');
  const error = searchParams.get('error');
  const errorDescription = searchParams.get('error_description');
  const hasDispatchedRef = useRef(false);

  const status = error ? 'error' : (code ? 'success' : 'processing');

  useEffect(() => {
    if (hasDispatchedRef.current) return;

    if (error) {
      hasDispatchedRef.current = true;
      if (window.opener) {
        window.opener.postMessage(
          { type: 'GITHUB_OAUTH_ERROR', error: errorDescription || error },
          window.location.origin
        );
        setTimeout(() => window.close(), 2500);
      }
      return;
    }

    if (code) {
      hasDispatchedRef.current = true;
      if (window.opener) {
        window.opener.postMessage(
          { type: 'GITHUB_OAUTH_CODE', code },
          window.location.origin
        );
        // Tự động đóng cửa sổ popup sau khi gửi message
        setTimeout(() => window.close(), 800);
      }
    }
  }, [code, error, errorDescription]);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        backgroundColor: '#0F172A',
        color: '#F8FAFC',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        padding: 24,
        textAlign: 'center',
      }}
    >
      <div
        style={{
          background: '#1E293B',
          borderRadius: 16,
          padding: '36px 32px',
          maxWidth: 420,
          width: '100%',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.4)',
          border: '1px solid #334155',
        }}
      >
        {status === 'processing' && (
          <>
            <Loader2 size={48} className="animate-spin" style={{ color: '#3B82F6', margin: '0 auto 16px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px' }}>Đang xác thực với GitHub...</h2>
            <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Vui lòng đợi trong giây lát</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle size={48} style={{ color: '#10B981', margin: '0 auto 16px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px', color: '#10B981' }}>
              Xác thực thành công!
            </h2>
            <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>
              Đang chuyển hướng dữ liệu về hệ thống. Cửa sổ này sẽ tự đóng...
            </p>
          </>
        )}

        {status === 'error' && (
          <>
            <AlertCircle size={48} style={{ color: '#EF4444', margin: '0 auto 16px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px', color: '#EF4444' }}>
              Xác thực thất bại
            </h2>
            <p style={{ fontSize: 13, color: '#94A3B8', margin: '0 0 16px' }}>
              {errorDescription || error || 'Không tìm thấy mã xác thực từ GitHub.'}
            </p>
            <button
              onClick={() => window.close()}
              style={{
                backgroundColor: '#334155',
                color: '#FFFFFF',
                border: 'none',
                padding: '8px 16px',
                borderRadius: 8,
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Đóng cửa sổ
            </button>
          </>
        )}
      </div>
    </div>
  );
}
