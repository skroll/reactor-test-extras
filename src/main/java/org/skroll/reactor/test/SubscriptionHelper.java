package org.skroll.reactor.test;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.reactivestreams.Subscription;
import reactor.core.publisher.Operators;

final class SubscriptionHelper {
  /**
   * Atomically requests from the Subscription in the field if not null, otherwise accumulates
   * the request amount in the requested field to be requested once the field is set to non-null.
   * @param subscriptionField the target field that may already contain a Subscription
   * @param requestedField the current requested amount field
   * @param instance the instance containing the subscription and requested fields
   * @param n the request amount, positive (verified)
   */
  public static <T> void deferredRequest(
      final AtomicReferenceFieldUpdater<T, Subscription> subscriptionField,
      final AtomicLongFieldUpdater<T> requestedField, final T instance, final long n) {
    Subscription s = subscriptionField.get(instance);
    if (s != null) {
      s.request(n);
    } else {
      if (Operators.validate(n)) {
        Operators.addCap(requestedField, instance, n);

        s = subscriptionField.get(instance);
        if (s != null) {
          long r = requestedField.getAndSet(instance, 0L);
          if (r != 0L) {
            s.request(r);
          }
        }
      }
    }
  }
}
