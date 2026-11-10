import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { refreshSession } from '../../api/session';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { postLoginPath } from './authContext';

/**
 * Landing page after Google sign-in. The API has set the refresh cookie; exchanging it for an access token here
 * means no token ever travels in a URL.
 */
export function OAuthCompletePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void refreshSession().then((auth) => {
      if (auth) {
        navigate(postLoginPath(auth.user, params.get('redirect')), { replace: true });
      } else {
        navigate('/login?error=google_failed', { replace: true });
      }
    });
  }, [navigate, params]);

  return <PageSpinner label="Finishing sign-in" />;
}
