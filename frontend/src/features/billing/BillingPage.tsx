import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useState } from 'react';
import { billingApi, meApi } from '../../api/endpoints';
import type { CheckoutResponse } from '../../api/models';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { buttonClass } from '../../components/ui/styles';
import { formatDate, formatRupees, humanize } from '../../lib/labels';
import { useAuth } from '../auth/authContext';
import { describeError } from '../auth/errorMessages';
import { openRazorpayCheckout } from './razorpay';

const POLL_MS = 2000;
const PRO_PLAN = 'PRO_MONTHLY';

export function BillingPage() {
  const { user, setUser } = useAuth();
  const queryClient = useQueryClient();
  const [checkout, setCheckout] = useState<CheckoutResponse | null>(null);
  const [awaiting, setAwaiting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  const plans = useQuery({ queryKey: ['billing', 'plans'], queryFn: billingApi.plans, staleTime: 60 * 60 * 1000 });
  const subscription = useQuery({
    queryKey: ['billing', 'subscription'],
    queryFn: billingApi.subscription,
    refetchInterval: awaiting ? POLL_MS : false,
  });

  const refreshUser = useCallback(async () => {
    try {
      setUser(await meApi.get());
    } catch {
      // the badge catches up on the next token refresh
    }
    void queryClient.invalidateQueries({ queryKey: ['usage'] });
  }, [setUser, queryClient]);

  // After checkout the plan only changes once the provider's webhook lands: poll until the subscription is ACTIVE.
  useEffect(() => {
    if (awaiting && subscription.data?.status === 'ACTIVE') {
      setAwaiting(false);
      setCheckout(null);
      setNotice('Welcome to Pro! Your plan is active.');
      void refreshUser();
    }
  }, [awaiting, subscription.data?.status, refreshUser]);

  async function upgrade() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const started = await billingApi.subscribe(PRO_PLAN);
      if (started.provider === 'RAZORPAY' && started.keyId) {
        const payment = await openRazorpayCheckout({
          keyId: started.keyId,
          subscriptionId: started.subscriptionId,
          name: started.name ?? user?.name,
          email: started.email ?? user?.email,
        });
        if (!payment) {
          setNotice('Checkout closed — no payment was taken.');
          return;
        }
        const verified = await billingApi.verify({
          paymentId: payment.razorpay_payment_id,
          subscriptionId: payment.razorpay_subscription_id,
          signature: payment.razorpay_signature,
        });
        setNotice(verified.status === 'ACTIVE' ? null : 'Payment received. Activating your plan…');
        setAwaiting(true);
        void queryClient.invalidateQueries({ queryKey: ['billing', 'subscription'] });
      } else {
        setCheckout(started);
      }
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function completeMock() {
    if (!checkout) return;
    setBusy(true);
    setError(null);
    try {
      const sub = await billingApi.mockComplete(checkout.subscriptionId);
      queryClient.setQueryData(['billing', 'subscription'], sub);
      if (sub.status === 'ACTIVE') {
        setCheckout(null);
        setNotice('Welcome to Pro! Your plan is active (test mode).');
        await refreshUser();
      } else {
        setAwaiting(true);
      }
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    setBusy(true);
    setError(null);
    try {
      const sub = await billingApi.cancel();
      queryClient.setQueryData(['billing', 'subscription'], sub);
      setConfirmCancel(false);
      setNotice(sub.currentEnd ? `Your subscription is cancelled. Pro stays active until ${formatDate(sub.currentEnd)}.` : 'Your subscription is cancelled.');
      await refreshUser();
    } catch (err) {
      setError(err);
      setConfirmCancel(false);
    } finally {
      setBusy(false);
    }
  }

  if (plans.isPending || subscription.isPending) return <PageSpinner label="Loading billing" />;

  const sub = subscription.data;
  const isPro = user?.plan === 'PRO';
  const cancellable = sub && ['ACTIVE', 'AUTHENTICATED', 'PENDING'].includes(sub.status);

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader title="Plans & billing" kicker={isPro ? '★ Pro plan' : 'Free plan'} kickerColor={isPro ? 'bg-nb-violet' : 'bg-nb-green'} />

      {notice && <Alert tone="success">{notice}</Alert>}
      {error !== null && <Alert>{describeError(error)}</Alert>}
      {plans.isError && <Alert>{describeError(plans.error)}</Alert>}
      {awaiting && (
        <p role="status" className="nb-accent bg-nb-yellow px-4 py-3 font-bold">
          Waiting for the payment provider to confirm your subscription…
        </p>
      )}

      <section aria-labelledby="current-heading" className="nb-card p-6">
        <h2 id="current-heading" className="nb-heading text-2xl">
          Current plan
        </h2>
        <p className="mt-2 text-lg font-bold">{isPro ? 'Pro' : 'Free'}</p>
        {sub ? (
          <dl className="mt-3 grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
            <dt className="font-mono text-xs font-bold uppercase">Subscription</dt>
            <dd className="font-medium">
              {sub.planId === PRO_PLAN ? 'Pro monthly' : sub.planId} · <span className="nb-badge bg-nb-blue">{humanize(sub.status)}</span>
            </dd>
            {sub.currentEnd && (
              <>
                <dt className="font-mono text-xs font-bold uppercase">{sub.status === 'CANCELLED' ? 'Pro until' : 'Renews'}</dt>
                <dd className="font-medium">{formatDate(sub.currentEnd)}</dd>
              </>
            )}
            <dt className="font-mono text-xs font-bold uppercase">Provider</dt>
            <dd className="font-medium">{sub.provider === 'MOCK' ? 'Test mode (no real payments)' : humanize(sub.provider)}</dd>
          </dl>
        ) : (
          <p className="mt-1 font-medium text-muted">No subscription. You're on the free plan.</p>
        )}
        {cancellable && (
          <button type="button" className={`${buttonClass.danger} mt-4`} onClick={() => setConfirmCancel(true)} disabled={busy}>
            Cancel subscription
          </button>
        )}
      </section>

      <section aria-labelledby="plans-heading">
        <h2 id="plans-heading" className="nb-heading text-2xl">
          Plans
        </h2>
        <ul className="mt-4 grid gap-6 md:grid-cols-2">
          {(plans.data ?? []).map((plan) => {
            const current = plan.id === PRO_PLAN && isPro;
            return (
              <li key={plan.id} className={`nb-accent p-6 ${plan.id === PRO_PLAN ? 'bg-nb-violet' : 'bg-nb-green'}`}>
                <h3 className="nb-heading text-2xl">{plan.name}</h3>
                <p className="mt-2">
                  <span className="nb-heading text-5xl">{formatRupees(plan.priceInPaise)}</span>
                  {plan.priceInPaise > 0 && <span className="font-bold"> / {plan.interval.toLowerCase()}</span>}
                </p>
                <ul className="mt-4 space-y-1.5 font-medium">
                  {plan.features.map((f) => (
                    <li key={f} className="flex gap-2">
                      <span aria-hidden="true">✓</span>
                      {f}
                    </li>
                  ))}
                </ul>
                {plan.id === PRO_PLAN &&
                  (current ? (
                    <p className="nb-badge mt-5 bg-white">Your current plan</p>
                  ) : (
                    <button type="button" className={`${buttonClass.ink} mt-5`} onClick={() => void upgrade()} disabled={busy || awaiting || checkout !== null}>
                      {busy && !checkout ? 'Starting checkout…' : `Upgrade to ${plan.name}`}
                    </button>
                  ))}
              </li>
            );
          })}
        </ul>
      </section>

      {checkout && checkout.provider !== 'RAZORPAY' && (
        <section aria-labelledby="mock-heading" className="nb-accent border-dashed bg-nb-orange p-6">
          <p className="nb-badge bg-white">Test mode — no real payment</p>
          <h2 id="mock-heading" className="nb-heading mt-3 text-2xl">
            Simulated checkout
          </h2>
          <p className="mt-2 font-medium">
            This server uses the mock payment provider. Nothing is charged: the button below simulates the provider confirming
            subscription <span className="font-mono">{checkout.subscriptionId}</span> for {formatRupees(checkout.priceInPaise)}.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <button type="button" className={buttonClass.ink} onClick={() => void completeMock()} disabled={busy}>
              {busy ? 'Completing…' : 'Complete test payment'}
            </button>
            <button type="button" className={buttonClass.secondary} onClick={() => setCheckout(null)} disabled={busy}>
              Cancel
            </button>
          </div>
        </section>
      )}

      <ConfirmDialog
        open={confirmCancel}
        title="Cancel your subscription?"
        confirmLabel="Cancel subscription"
        cancelLabel="Keep Pro"
        busy={busy}
        onConfirm={() => void cancel()}
        onCancel={() => setConfirmCancel(false)}
      >
        You keep Pro until the end of the current billing period.
      </ConfirmDialog>
    </div>
  );
}
