const CHECKOUT_SRC = 'https://checkout.razorpay.com/v1/checkout.js';

export interface RazorpaySuccess {
  razorpay_payment_id: string;
  razorpay_subscription_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  subscription_id: string;
  name: string;
  description?: string;
  prefill?: { name?: string; email?: string };
  theme?: { color?: string };
  handler: (response: RazorpaySuccess) => void;
  modal?: { ondismiss?: () => void };
}

type RazorpayCtor = new (options: RazorpayOptions) => { open: () => void };

let loading: Promise<RazorpayCtor> | null = null;

/** Loads Razorpay Checkout on demand (only when the user upgrades). */
export function loadRazorpay(): Promise<RazorpayCtor> {
  const existing = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay;
  if (existing) return Promise.resolve(existing);
  loading ??= new Promise<RazorpayCtor>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = CHECKOUT_SRC;
    script.async = true;
    script.onload = () => {
      const ctor = (window as unknown as { Razorpay?: RazorpayCtor }).Razorpay;
      if (ctor) resolve(ctor);
      else reject(new Error('Razorpay Checkout failed to initialise'));
    };
    script.onerror = () => {
      loading = null;
      reject(new Error('Could not load Razorpay Checkout. Check your connection or ad blocker.'));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** Opens Checkout for a subscription; resolves with the signed payment, or null when the user closes it. */
export async function openRazorpayCheckout(options: { keyId: string; subscriptionId: string; name?: string; email?: string }): Promise<RazorpaySuccess | null> {
  const Razorpay = await loadRazorpay();
  return new Promise((resolve) => {
    const checkout = new Razorpay({
      key: options.keyId,
      subscription_id: options.subscriptionId,
      name: 'AI Interview Coach',
      description: 'Pro plan (monthly)',
      prefill: { name: options.name, email: options.email },
      theme: { color: '#ffd84d' },
      handler: (response) => resolve(response),
      modal: { ondismiss: () => resolve(null) },
    });
    checkout.open();
  });
}
