package org.skroll.reactor.test;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Operators;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A subscriber that records events and allows making assertions about them.
 *
 * <p>You can override the onSubscribe, onNext, onError, onComplete, request and
 * cancel methods but not the others (this is by design).
 *
 * <p>The TestSubscriber implements Disposable for convenience where dispose calls cancel.
 *
 * <p>When calling the default request method, you are requesting on behalf of the
 * wrapped actual subscriber.
 *
 * @param <T> the value type
 */
public class TestSubscriber<T> extends BaseTestConsumer<T, TestSubscriber<T>> implements Subscriber<T>, Subscription, Disposable {
  /** The actual subscriber to forward events to. */
  private final Subscriber<? super T> actual;

  /** Makes sure the incoming Subscriptions get cancelled immediately. */
  private volatile boolean cancelled;

  /** Holds the current subscription if any. */
  private final AtomicReference<Subscription> subscription;

  /** Holds the requested amount until a subscription arrives. */
  private final AtomicLong missedRequested;


  /**
   * Creates a TestSubscriber with Long.MAX_VALUE initial request.
   * @param <T> the value type
   * @return the new TestSubscriber instance.
   */
  public static <T> TestSubscriber<T> create() {
    return new TestSubscriber<T>();
  }

  /**
   * Creates a TestSubscriber with the given initial request.
   * @param <T> the value type
   * @param initialRequested the initial requested amount
   * @return the new TestSubscriber instance.
   */
  public static <T> TestSubscriber<T> create(long initialRequested) {
    return new TestSubscriber<T>(initialRequested);
  }

  /**
   * Constructs a forwarding TestSubscriber.
   * @param <T> the value type received
   * @param delegate the actual Subscriber to forward events to
   * @return the new TestObserver instance
   */
  public static <T> TestSubscriber<T> create(Subscriber<? super T> delegate) {
    return new TestSubscriber<T>(delegate);
  }

  /**
   * Constructs a non-forwarding TestSubscriber with an initial request value of Long.MAX_VALUE.
   */
  public TestSubscriber() {
    this(EmptySubscriber.INSTANCE, Long.MAX_VALUE);
  }

  /**
   * Constructs a non-forwarding TestSubscriber with the specified initial request value.
   * <p>The TestSubscriber doesn't validate the initialRequest value so one can
   * test sources with invalid values as well.
   * @param initialRequest the initial request value
   */
  public TestSubscriber(long initialRequest) {
    this(EmptySubscriber.INSTANCE, initialRequest);
  }

  /**
   * Constructs a forwarding TestSubscriber but leaves the requesting to the wrapped subscriber.
   * @param actual the actual Subscriber to forward events to
   */
  public TestSubscriber(Subscriber<? super T> actual) {
    this(actual, Long.MAX_VALUE);
  }

  /**
   * Constructs a forwarding TestSubscriber with the specified initial request value.
   * <p>The TestSubscriber doesn't validate the initialRequest value so one can
   * test sources with invalid values as well.
   * @param actual the actual Subscriber to forward events to
   * @param initialRequest the initial request value
   */
  public TestSubscriber(Subscriber<? super T> actual, long initialRequest) {
    super();
    if (initialRequest < 0) {
      throw new IllegalArgumentException("Negative initial request not allowed");
    }
    this.actual = actual;
    this.subscription = new AtomicReference<Subscription>();
    this.missedRequested = new AtomicLong(initialRequest);
  }


  @SuppressWarnings("unchecked")
  @Override
  public void onSubscribe(final Subscription s) {
    lastThread = Thread.currentThread();

    if (s == null) {
      errors.add(new NullPointerException("onSubscribe received a null Subscription"));
      return;
    }
    if (!subscription.compareAndSet(null, s)) {
      s.cancel();
      if (subscription.get() != Operators.cancelledSubscription()) {
        errors.add(new IllegalStateException("onSubscribe received multiple subscriptions: " + s));
      }
      return;
    }

    actual.onSubscribe(s);

    long mr = missedRequested.getAndSet(0L);
    if (mr != 0L) {
      s.request(mr);
    }

    onStart();
  }

  /**
   * Called after the onSubscribe is called and handled.
   */
  protected void onStart() {

  }

  @Override
  public void onNext(T t) {
    if (!checkSubscriptionOnce) {
      checkSubscriptionOnce = true;
      if (subscription.get() == null) {
        errors.add(new IllegalStateException("onSubscribe not called in proper order"));
      }
    }
    lastThread = Thread.currentThread();

    values.add(t);

    if (t == null) {
      errors.add(new NullPointerException("onNext received a null value"));
    }

    actual.onNext(t);
  }

  @Override
  public void onError(Throwable t) {
    if (!checkSubscriptionOnce) {
      checkSubscriptionOnce = true;
      if (subscription.get() == null) {
        errors.add(new NullPointerException("onSubscribe not called in proper order"));
      }
    }
    try {
      lastThread = Thread.currentThread();
      errors.add(t);

      if (t == null) {
        errors.add(new IllegalStateException("onError received a null Throwable"));
      }

      actual.onError(t);
    } finally {
      done.countDown();
    }
  }

  @Override
  public void onComplete() {
    if (!checkSubscriptionOnce) {
      checkSubscriptionOnce = true;
      if (subscription.get() == null) {
        errors.add(new IllegalStateException("onSubscribe not called in proper order"));
      }
    }
    try {
      lastThread = Thread.currentThread();
      completions++;

      actual.onComplete();
    } finally {
      done.countDown();
    }
  }

  @Override
  public final void request(long n) {
    SubscriptionHelper.deferredRequest(subscription, missedRequested, n);
  }

  @Override
  public final void cancel() {
    if (!cancelled) {
      cancelled = true;
      SubscriptionHelper.cancel(subscription);
    }
  }

  /**
   * Returns true if this TestSubscriber has been cancelled.
   * @return true if this TestSubscriber has been cancelled
   */
  public final boolean isCancelled() {
    return cancelled;
  }

  @Override
  public final void dispose() {
    cancel();
  }

  @Override
  public final boolean isDisposed() {
    return cancelled;
  }

  // state retrieval methods

  /**
   * Returns true if this TestSubscriber received a subscription.
   * @return true if this TestSubscriber received a subscription
   */
  public final boolean hasSubscription() {
    return subscription.get() != null;
  }

  // assertion methods

  /**
   * Assert that the onSubscribe method was called exactly once.
   * @return this
   */
  @Override
  public final TestSubscriber<T> assertSubscribed() {
    if (subscription.get() == null) {
      throw fail("Not subscribed!");
    }
    return this;
  }

  /**
   * Assert that the onSubscribe method hasn't been called at all.
   * @return this
   */
  @Override
  public final TestSubscriber<T> assertNotSubscribed() {
    if (subscription.get() != null) {
      throw fail("Subscribed!");
    } else
    if (!errors.isEmpty()) {
      throw fail("Not subscribed but errors found");
    }
    return this;
  }


  /**
   * Run a check consumer with this TestSubscriber instance.
   * @param check the check consumer to run
   * @return this
   */
  public final TestSubscriber<T> assertOf(Consumer<? super TestSubscriber<T>> check) {
    try {
      check.accept(this);
    } catch (Throwable ex) {
      throw Exceptions.propagate(ex);
    }
    return this;
  }

  /**
   * Calls {@link #request(long)} and returns this.
   * <p>History: 2.0.1 - experimental
   * @param n the request amount
   * @return this
   * @since 2.1
   */
  public final TestSubscriber<T> requestMore(long n) {
    request(n);
    return this;
  }

  /**
   * A subscriber that ignores all events and does not report errors.
   */
  enum EmptySubscriber implements Subscriber<Object> {
    INSTANCE;

    @Override
    public void onSubscribe(Subscription s) {
    }

    @Override
    public void onNext(Object t) {
    }

    @Override
    public void onError(Throwable t) {
    }

    @Override
    public void onComplete() {
    }
  }
}
