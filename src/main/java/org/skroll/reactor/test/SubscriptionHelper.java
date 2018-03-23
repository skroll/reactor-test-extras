package org.skroll.reactor.test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;
import reactor.core.publisher.Operators;

final class SubscriptionHelper {
  /**
   * Atomically swaps in the common cancelled subscription instance
   * and cancels the previous subscription if any.
   * @param field the target field to dispose the contents of
   * @return true if the swap from the non-cancelled instance to the
   *     common cancelled instance happened in the caller's thread (allows
   *     further one-time actions).
   */
  public static boolean cancel(final AtomicReference<Subscription> field) {
    Subscription current = field.get();
    if (current != Operators.cancelledSubscription()) {
      current = field.getAndSet(Operators.cancelledSubscription());
      if (current != Operators.cancelledSubscription()) {
        if (current != null) {
          current.cancel();
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Atomically requests from the Subscription in the field if not null, otherwise accumulates
   * the request amount in the requested field to be requested once the field is set to non-null.
   * @param field the target field that may already contain a Subscription
   * @param requested the current requested amount
   * @param n the request amount, positive (verified)
   */
  public static void deferredRequest(final AtomicReference<Subscription> field,
      final AtomicLong requested, final long n) {
    Subscription s = field.get();
    if (s != null) {
      s.request(n);
    } else {
      if (Operators.validate(n)) {
        addCap(requested, n);

        s = field.get();
        if (s != null) {
          long r = requested.getAndSet(0L);
          if (r != 0L) {
            s.request(r);
          }
        }
      }
    }
  }

  private static long addCap(final AtomicLong requested, final long n) {
    for (;;) {
      long r = requested.get();
      if (r == Long.MAX_VALUE) {
        return Long.MAX_VALUE;
      }
      long u = Operators.addCap(r, n);
      if (requested.compareAndSet(r, u)) {
        return r;
      }
    }
  }
}
