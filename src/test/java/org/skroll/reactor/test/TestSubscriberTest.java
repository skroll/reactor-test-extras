package org.skroll.reactor.test;

import org.junit.Test;
import org.mockito.InOrder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.skroll.reactor.test.exceptions.TestException;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import static org.junit.jupiter.api.Assertions.*;

public class TestSubscriberTest {
  @Test
  public void testValuesList() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(1, 2, 3).subscribe(ts);
    List<Integer> values = ts.values();
    assertEquals(values.size(), 3);
    assertEquals(values.get(0).intValue(), 1);
    assertEquals(values.get(1).intValue(), 2);
    assertEquals(values.get(2).intValue(), 3);
  }

  @Test
  public void testEventsList() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(1, 2, 3).subscribe(ts);
    List<List<Object>> events = ts.getEvents();
    assertEquals(events.size(), 3);

    List<Object> values = events.get(0);
    assertEquals(3, values.size());
    assertEquals(1, values.get(0));
    assertEquals(2, values.get(1));
    assertEquals(3, values.get(2));

    List<Object> errors = events.get(1);
    assertEquals(0, errors.size());

    List<Object> completions = events.get(2);
    assertEquals(1, completions.size());
    final Signal<?> signal = (Signal<?>) completions.get(0);

    assertEquals(SignalType.ON_COMPLETE, signal.getType());
  }

  @Test
  public void testAssertNever() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(1, 2, 3).subscribe(ts);
    assertThrows(AssertionError.class, () -> ts.assertNever(2));
  }

  @Test
  public void testValueCount() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(1, 2, 3).subscribe(ts);
    assertEquals(3, ts.valueCount());
  }

  @Test
  public void testInvalidInitialRequest() {
    assertThrows(IllegalArgumentException.class, () -> new TestSubscriber<Integer>(-1));
    assertThrows(IllegalArgumentException.class, () -> new TestSubscriber<Integer>(TestSubscriber.EmptySubscriber.INSTANCE, -1));
  }

  @Test
  public void testDoubleCancel() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertFalse(ts.isCancelled());
    ts.cancel();
    assertTrue(ts.isCancelled());
    ts.cancel();
    assertTrue(ts.isCancelled());
  }

  @Test
  public void testAssert() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2));
    TestSubscriber<Integer> o = new TestSubscriber<>();
    oi.subscribe(o);

    o.assertValues(1, 2);
    o.assertValueCount(2);
    o.assertTerminated();
  }

  @Test
  public void testAssertNotMatchCount() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2));
    TestSubscriber<Integer> o = new TestSubscriber<>();
    oi.subscribe(o);

    assertThrows(AssertionError.class, () -> o.assertValue(1));
    o.assertValueCount(2);
    o.assertTerminated();
  }

  @Test
  public void testAssertNotMatchValue() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2));
    TestSubscriber<Integer> o = new TestSubscriber<>();
    oi.subscribe(o);

    assertThrows(AssertionError.class, () -> o.assertValues(1, 3));
    o.assertValueCount(2);
    o.assertTerminated();
  }

  @Test
  public void assertNeverAtNotMatchingValue() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2));
    TestSubscriber<Integer> o = new TestSubscriber<>();
    oi.subscribe(o);

    o.assertNever(3);
    o.assertValueCount(2);
    o.assertTerminated();
  }

  @Test
  public void assertNeverAtMatchingPredicate() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(1, 2).subscribe(ts);
    ts.assertValues(1, 2);

    assertThrows(AssertionError.class, () -> ts.assertNever(o -> o == 1));
  }

  @Test
  public void assertNeverAtNotMatchingPredicate() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.just(2, 3).subscribe(ts);

    ts.assertNever(o -> o == 1);
  }

  @Test
  public void testAssertTerminalEventNotReceived() {
    EmitterProcessor<Integer> p = EmitterProcessor.create();
    TestSubscriber<Integer> o = new TestSubscriber<>();
    p.subscribe(o);

    p.onNext(1);
    p.onNext(2);

    o.assertValues(1, 2);
    o.assertValueCount(2);
    assertThrows(AssertionError.class, o::assertTerminated);
  }

  @Test
  public void testWrappingMock() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2));
    Subscriber<Integer> mockSubscriber = TestHelper.mockSubscriber();

    oi.subscribe(new TestSubscriber<>(mockSubscriber));

    InOrder inOrder = inOrder(mockSubscriber);
    inOrder.verify(mockSubscriber, times(1)).onNext(1);
    inOrder.verify(mockSubscriber, times(1)).onNext(2);
    inOrder.verify(mockSubscriber, times(1)).onComplete();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testWrappingMockWhenUnsubscribeInvolved() {
    Flux<Integer> oi = Flux.fromIterable(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9)).take(2);
    Subscriber<Integer> mockSubscriber = TestHelper.mockSubscriber();
    oi.subscribe(new TestSubscriber<>(mockSubscriber));

    InOrder inOrder = inOrder(mockSubscriber);
    inOrder.verify(mockSubscriber, times(1)).onNext(1);
    inOrder.verify(mockSubscriber, times(1)).onNext(2);
    inOrder.verify(mockSubscriber, times(1)).onComplete();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testAssertError() {
    RuntimeException e = new RuntimeException("Oops");
    TestSubscriber<Object> subscriber = new TestSubscriber<>();
    Flux.error(e).subscribe(subscriber);
    subscriber.assertError(e);
  }

  @Test
  public void testAwaitTerminalEventWithDuration() {
    TestSubscriber<Object> ts = new TestSubscriber<>();
    Flux.just(1).subscribe(ts);
    ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
    ts.assertTerminated();
  }

  @Test
  public void testAwaitTerminalEventWithDurationAndUnsubscribeOnTimeout() {
    TestSubscriber<Object> ts = new TestSubscriber<>();
    final AtomicBoolean unsub = new AtomicBoolean(false);
    Flux.just(1)
        .doOnCancel(() -> unsub.set(true))
        .delayElements(Duration.ofMillis(1000))
        .subscribe(ts);
    ts.awaitTerminalEvent(100, TimeUnit.MILLISECONDS);
    ts.dispose();
    assertTrue(unsub.get());
  }

  @Test
  public void testNullDelegate1() {
    TestSubscriber<Integer> ts = new TestSubscriber<>(null);
    assertThrows(NullPointerException.class, ts::onComplete);
  }

  @Test
  public void testNullDelegate3() {
    TestSubscriber<Integer> ts = new TestSubscriber<>(null, 0L);
    assertThrows(NullPointerException.class, ts::onComplete);
  }

  @Test
  public void testDelegate1() {
    TestSubscriber<Integer> ts0 = new TestSubscriber<>();
    ts0.onSubscribe(Operators.emptySubscription());

    TestSubscriber<Integer> ts = new TestSubscriber<>(ts0);
    ts.onComplete();

    ts0.assertTerminated();
  }

  @Test
  public void testDelegate2() {
    TestSubscriber<Integer> ts1 = new TestSubscriber<>();
    TestSubscriber<Integer> ts2 = new TestSubscriber<>(ts1);
    ts2.onComplete();

    ts1.assertComplete();
  }

  @Test
  public void testDelegate3() {
    TestSubscriber<Integer> ts1 = new TestSubscriber<>();
    TestSubscriber<Integer> ts2 = new TestSubscriber<>(ts1, 0L);
    ts2.onComplete();
    ts1.assertComplete();
  }

  @Test
  public void testUnsubscribed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertFalse(ts.isCancelled());
  }

  @Test
  public void testNoErrors() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new TestException());

    assertThrows(AssertionError.class, ts::assertNoErrors);
  }

  @Test
  public void testNotCompleted() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    assertThrows(AssertionError.class, ts::assertComplete);
  }

  @Test
  public void testMultipleCompletions() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onComplete();
    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertComplete);
  }

  @Test
  public void testCompleted() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertNotComplete);
  }

  @Test
  public void testMultipleCompletions2() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onComplete();
    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertNotComplete);
  }

//  @Test
//  public void testMultipleErrors() {
//    TestSubscriber<Integer> ts = new TestSubscriber<>();
//    ts.onSubscribe(Operators.emptySubscription());
//    ts.onError(new TestException());
//    ts.onError(new TestException());
//    try {
//      ts.assertNoErrors();
//    } catch (AssertionError ex) {
//      Throwable e = ex.getCause();
//      if (!(e instanceof CompositeException)) {
//        fail("Multiple Error present but the reported error doesn't have a composite cause!");
//      }
//      CompositeException ce = (CompositeException)e;
//      if (ce.size() != 2) {
//        ce.printStackTrace();
//      }
//      assertEquals(2, ce.size());
//      // expected
//      return;
//    }
//    fail("Multiple Error present but no assertion error!");
//  }

  /*
  @Test
  public void testMultipleErrors2() {
    TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
    ts.onSubscribe(EmptySubscription.INSTANCE);
    ts.onError(new TestException());
    ts.onError(new TestException());
    try {
      ts.assertError(TestException.class);
    } catch (AssertionError ex) {
      Throwable e = ex.getCause();
      if (!(e instanceof CompositeException)) {
        fail("Multiple Error present but the reported error doesn't have a composite cause!");
      }
      CompositeException ce = (CompositeException)e;
      assertEquals(2, ce.size());
      // expected
      return;
    }
    fail("Multiple Error present but no assertion error!");
  }

  @Test
  public void testMultipleErrors3() {
    TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
    ts.onSubscribe(EmptySubscription.INSTANCE);
    ts.onError(new TestException());
    ts.onError(new TestException());
    try {
      ts.assertError(new TestException());
    } catch (AssertionError ex) {
      Throwable e = ex.getCause();
      if (!(e instanceof CompositeException)) {
        fail("Multiple Error present but the reported error doesn't have a composite cause!");
      }
      CompositeException ce = (CompositeException)e;
      assertEquals(2, ce.size());
      // expected
      return;
    }
    fail("Multiple Error present but no assertion error!");
  }

  @Test
  public void testMultipleErrors4() {
    TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
    ts.onSubscribe(EmptySubscription.INSTANCE);
    ts.onError(new TestException());
    ts.onError(new TestException());
    try {
      ts.assertError(Functions.<Throwable>alwaysTrue());
    } catch (AssertionError ex) {
      Throwable e = ex.getCause();
      if (!(e instanceof CompositeException)) {
        fail("Multiple Error present but the reported error doesn't have a composite cause!");
      }
      CompositeException ce = (CompositeException)e;
      assertEquals(2, ce.size());
      // expected
      return;
    }
    fail("Multiple Error present but no assertion error!");
  }
  */

  @Test
  public void testDifferentError() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new TestException());
    assertThrows(AssertionError.class, () -> ts.assertError(new TestException()));
  }

  @Test
  public void testDifferentError2() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new RuntimeException());
    assertThrows(AssertionError.class, () -> ts.assertError(new TestException()));
  }

  @Test
  public void testDifferentError3() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new RuntimeException());
    assertThrows(AssertionError.class, () -> ts.assertError(TestException.class));
  }

  @Test
  public void testDifferentError4() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new RuntimeException());
    assertThrows(AssertionError.class, () -> ts.assertError(__ -> false));
  }

  @Test
  public void testErrorInPredicate() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onError(new RuntimeException());
    assertThrows(TestException.class, () -> ts.assertError(__ -> { throw new TestException(); }));
  }

  @Test
  public void testNoError() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertThrows(AssertionError.class, () -> ts.assertError(TestException.class));
  }

  @Test
  public void testNoError2() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertThrows(AssertionError.class, () -> ts.assertError(new TestException()));
  }

  @Test
  public void testNoError3() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertThrows(AssertionError.class, () -> ts.assertError(__ -> true));
  }

  @Test
  public void testInterruptTerminalEventAwait() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    final Thread t0 = Thread.currentThread();
    Scheduler.Worker w = Schedulers.parallel().createWorker();
    try {
      w.schedule(t0::interrupt, 200, TimeUnit.MILLISECONDS);

      try {
        if (ts.awaitTerminalEvent()) {
          fail("Did not interrupt wait!");
        }
      } catch (RuntimeException ex) {
        if (!(ex.getCause() instanceof InterruptedException)) {
          fail("The cause is not InterruptedException! " + ex.getCause());
        }
      }
    } finally {
      w.dispose();
    }
  }

  @Test
  public void testInterruptTerminalEventAwaitTimed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    final Thread t0 = Thread.currentThread();
    Scheduler.Worker w = Schedulers.parallel().createWorker();
    try {
      w.schedule(t0::interrupt, 200, TimeUnit.MILLISECONDS);

      try {
        if (ts.awaitTerminalEvent(5, TimeUnit.SECONDS)) {
          fail("Did not interrupt wait!");
        }
      } catch (RuntimeException ex) {
        if (!(ex.getCause() instanceof InterruptedException)) {
          fail("The cause is not InterruptedException! " + ex.getCause());
        }
      }
    } finally {
      Thread.interrupted(); // clear interrupted flag
      w.dispose();
    }
  }

  @Test
  public void testInterruptTerminalEventAwaitAndUnsubscribe() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    final Thread t0 = Thread.currentThread();
    Scheduler.Worker w = Schedulers.parallel().createWorker();
    try {
      w.schedule(t0::interrupt, 200, TimeUnit.MILLISECONDS);

      ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
      ts.dispose();
      if (!ts.isCancelled()) {
        fail("Did not unsubscribe!");
      }
    } finally {
      w.dispose();
    }
  }

  @Test
  public void testNoTerminalEventBut1Completed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertNotTerminated);
  }

  @Test
  public void testNoTerminalEventBut1Error() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.onError(new TestException());
    assertThrows(AssertionError.class, ts::assertNotTerminated);
  }

  @Test
  public void testNoTerminalEventBut1Error1Completed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.onComplete();
    ts.onError(new TestException());
    assertThrows(AssertionError.class, ts::assertNotTerminated);
  }
//
//  @Test
//  public void testNoTerminalEventBut2Errors() {
//    TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
//    ts.onSubscribe(EmptySubscription.INSTANCE);
//
//    ts.onError(new TestException());
//    ts.onError(new TestException());
//
//    try {
//      ts.assertNotTerminated();
//      fail("Failed to report there were terminal event(s)!");
//    } catch (AssertionError ex) {
//      // expected
//      Throwable e = ex.getCause();
//      if (!(e instanceof CompositeException)) {
//        fail("Multiple Error present but the reported error doesn't have a composite cause!");
//      }
//      CompositeException ce = (CompositeException)e;
//      assertEquals(2, ce.size());
//    }
//  }

  @Test
  public void testNoValues() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onNext(1);
    assertThrows(AssertionError.class, ts::assertNoValues);
  }

  @Test
  public void testAssertValueCount() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.onNext(1);
    ts.onNext(2);
    assertThrows(AssertionError.class, () -> ts.assertValueCount(3));
  }

  @Test
  public void testOnCompletedCrashCountsDownLatch() {
    TestSubscriber<Integer> ts0 = new TestSubscriber<Integer>() {
      @Override
      public void onComplete() {
        throw new TestException();
      }
    };
    TestSubscriber<Integer> ts = new TestSubscriber<>(ts0);

    assertThrows(TestException.class, ts::onComplete);
    ts.awaitTerminalEvent();
  }

  @Test
  public void testOnErrorCrashCountsDownLatch() {
    TestSubscriber<Integer> ts0 = new TestSubscriber<Integer>() {
      @Override
      public void onError(Throwable e) {
        throw new TestException();
      }
    };
    TestSubscriber<Integer> ts = new TestSubscriber<>(ts0);
    assertThrows(TestException.class, () -> ts.onError(new RuntimeException()));
    ts.awaitTerminalEvent();
  }


  @Test
  public void createDelegate() {
    TestSubscriber<Integer> ts1 = TestSubscriber.create();

    TestSubscriber<Integer> ts = TestSubscriber.create(ts1);

    ts.assertNotSubscribed();

    assertFalse(ts.hasSubscription());

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    assertThrows(AssertionError.class, ts::assertNotSubscribed);

    assertTrue(ts.hasSubscription());

    assertFalse(ts.isDisposed());

    ts.onNext(1);
    ts.onError(new TestException());
    ts.onComplete();

    ts1.assertValue(1).assertError(TestException.class).assertComplete();

    ts.dispose();

    assertTrue(ts.isDisposed());

    assertTrue(ts.isTerminated());

    assertSame(Thread.currentThread(), ts.lastThread());

    assertThrows(AssertionError.class, ts::assertNoValues);
    assertThrows(AssertionError.class, () -> ts.assertValueCount(0));

    ts.assertValueSequence(Collections.singletonList(1));

    assertThrows(AssertionError.class, () -> ts.assertValueSequence(Collections.singletonList(2)));

    ts.assertValueSet(Collections.singleton(1));

    assertThrows(AssertionError.class, () -> ts.assertValueSet(Collections.singleton(2)));
  }

  @Test
  public void assertError() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    assertThrows(AssertionError.class, () -> ts.assertError(TestException.class));
    assertThrows(AssertionError.class, () -> ts.assertError(new TestException()));
    assertThrows(AssertionError.class, () -> ts.assertErrorMessage(""));
    assertThrows(AssertionError.class, () -> ts.assertError(__ -> true));
    assertThrows(AssertionError.class, () -> ts.assertSubscribed());
    assertThrows(AssertionError.class, () -> ts.assertTerminated());

    ts.onSubscribe(new TestHelper.BooleanSubscription());
    ts.assertSubscribed();
    ts.assertNoErrors();

    TestException ex = new TestException("Forced failure");
    ts.onError(ex);
    ts.assertError(ex);
    ts.assertError(TestException.class);
    ts.assertErrorMessage("Forced failure");
    ts.assertError(__ -> true);
    ts.assertError(t -> t.getMessage() != null && t.getMessage().contains("Forced"));

    assertThrows(AssertionError.class, () -> ts.assertErrorMessage(""));
    assertThrows(AssertionError.class, () -> ts.assertError(new RuntimeException()));
    assertThrows(AssertionError.class, () -> ts.assertError(IOException.class));
    assertThrows(AssertionError.class, () -> ts.assertNoErrors());
    assertThrows(AssertionError.class, () -> ts.assertError(__ -> false));

    ts.assertTerminated();
    ts.assertValueCount(0);
    ts.assertNoValues();
  }

  @Test
  public void emptySubscriberEnum() {
    assertEquals(1, TestSubscriber.EmptySubscriber.values().length);
    assertNotNull(TestSubscriber.EmptySubscriber.valueOf("INSTANCE"));
  }

  @Test
  public void valueAndClass() {
    assertEquals("null", TestSubscriber.valueAndClass(null));
    assertEquals("1 (class: Integer)", TestSubscriber.valueAndClass(1));
  }

  @Test
  public void assertFailure() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onError(new TestException("Forced failure"));

    ts.assertFailure(TestException.class);

    ts.assertFailure(__ -> true);

    ts.assertFailureAndMessage(TestException.class, "Forced failure");

    ts.onNext(1);

    ts.assertFailure(TestException.class, 1);

    ts.assertFailure(__ -> true, 1);

    ts.assertFailureAndMessage(TestException.class, "Forced failure", 1);
  }

//  @Test
//  public void assertFuseable() {
//    TestSubscriber<Integer> ts = TestSubscriber.create();
//
//    ts.onSubscribe(new TestHelper.BooleanSubscription());
//
//    ts.assertNotFuseable();
//
//    try {
//      ts.assertFuseable();
//      throw new RuntimeException("Should have thrown");
//    } catch (AssertionError ex) {
//      // expected
//    }
//
//    try {
//      ts.assertFusionMode(QueueFuseable.SYNC);
//      throw new RuntimeException("Should have thrown");
//    } catch (AssertionError ex) {
//      // expected
//    }
//    ts = TestSubscriber.create();
//    ts.setInitialFusionMode(QueueFuseable.ANY);
//
//    ts.onSubscribe(new ScalarSubscription<Integer>(ts, 1));
//
//    ts.assertFuseable();
//
//    ts.assertFusionMode(QueueFuseable.SYNC);
//
//    try {
//      ts.assertFusionMode(QueueFuseable.NONE);
//      throw new RuntimeException("Should have thrown");
//    } catch (AssertionError ex) {
//      // expected
//    }
//
//    try {
//      ts.assertNotFuseable();
//      throw new RuntimeException("Should have thrown");
//    } catch (AssertionError ex) {
//      // expected
//    }
//
//  }

  @Test
  public void assertTerminated() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.assertNotTerminated();

    ts.onError(null);

    assertThrows(AssertionError.class, ts::assertNotTerminated);
  }

  @Test
  public void assertOf() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.assertOf(TestSubscriber::assertNotSubscribed);
    assertThrows(AssertionError.class, () -> ts.assertOf(TestSubscriber::assertSubscribed));
    assertThrows(IllegalArgumentException.class, () ->
        ts.assertOf(__ -> { throw new IllegalArgumentException(); })
    );
  }

  @Test
  public void assertResult() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onComplete();

    ts.assertResult();
    assertThrows(AssertionError.class, () -> ts.assertResult(1));

    ts.onNext(1);

    ts.assertResult(1);
    assertThrows(AssertionError.class, () -> ts.assertResult(2));
    assertThrows(AssertionError.class, () -> ts.assertResult());
  }

  @Test
  public void await() throws Exception {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    assertFalse(ts.await(100, TimeUnit.MILLISECONDS));

    ts.awaitDone(100, TimeUnit.MILLISECONDS);

    assertTrue(ts.isDisposed());

    assertFalse(ts.awaitTerminalEvent(100, TimeUnit.MILLISECONDS));

    assertEquals(0, ts.completions());
    assertEquals(0, ts.errorCount());

    ts.onComplete();

    assertTrue(ts.await(100, TimeUnit.MILLISECONDS));

    ts.await();

    ts.awaitDone(5, TimeUnit.SECONDS);

    assertEquals(1, ts.completions());
    assertEquals(0, ts.errorCount());

    assertTrue(ts.awaitTerminalEvent());

    final TestSubscriber<Integer> ts1 = TestSubscriber.create();

    ts1.onSubscribe(new TestHelper.BooleanSubscription());

    Schedulers.single().schedule(ts1::onComplete, 200, TimeUnit.MILLISECONDS);

    ts1.await();

    ts1.assertValueSet(Collections.emptySet());
  }

  /*
  @Test
  public void errors() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    assertEquals(0, ts.errors().size());

    ts.onError(new TestException());

    assertEquals(1, ts.errors().size());

    TestHelper.assertError(ts.errors(), 0, TestException.class);
  }
*/
//  @SuppressWarnings("unchecked")
//  @Test
//  public void onNext() {
//    TestSubscriber<Integer> ts = TestSubscriber.create();
//
//    ts.onSubscribe(new TestHelper.BooleanSubscription());
//
//    assertEquals(0, ts.valueCount());
//
//    assertEquals(Collections.emptyList(), ts.values());
//
//    ts.onNext(1);
//
//    assertEquals(Collections.singletonList(1), ts.values());
//
//    ts.cancel();
//
//    assertTrue(ts.isCancelled());
//    assertTrue(ts.isDisposed());
//
//    ts.assertValue(1);
//
//    assertEquals(Arrays.asList(Collections.singletonList(1), Collections.emptyList(), Collections.emptyList()), ts.getEvents());
//
//    ts.onComplete();
//
//    assertEquals(Arrays.asList(Collections.singletonList(1), Collections.emptyList(), Collections.singletonList(Notification.createOnComplete())), ts.getEvents());
//  }

//  @Test
//  public void fusionModeToString() {
//    assertEquals("NONE", TestSubscriber.fusionModeToString(QueueFuseable.NONE));
//    assertEquals("SYNC", TestSubscriber.fusionModeToString(QueueFuseable.SYNC));
//    assertEquals("ASYNC", TestSubscriber.fusionModeToString(QueueFuseable.ASYNC));
//    assertEquals("Unknown(100)", TestSubscriber.fusionModeToString(100));
//  }

  @Test
  public void multipleTerminals() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.assertNotComplete();

    ts.onComplete();

    assertThrows(Throwable.class, ts::assertNotComplete);
    ts.assertTerminated();

    ts.onComplete();
    assertThrows(Throwable.class, ts::assertComplete);
    assertThrows(Throwable.class, ts::assertTerminated);
    assertThrows(Throwable.class, ts::assertNotComplete);
  }

  @Test
  public void assertValue() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());
    assertThrows(Throwable.class, () -> ts.assertValue(1));

    ts.onNext(1);

    ts.assertValue(1);
    assertThrows(Throwable.class, () -> ts.assertValue(2));

    ts.onNext(2);
    assertThrows(Throwable.class, () -> ts.assertValue(1));
  }

  @Test
  public void onNextMisbehave() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onNext(1);

    ts.assertError(IllegalStateException.class);

    ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onNext(null);

    ts.assertFailure(NullPointerException.class, (Integer)null);
  }

  @Test
  public void awaitTerminalEventInterrupt() {
    final TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    Thread.currentThread().interrupt();

    ts.awaitTerminalEvent();

    assertTrue(Thread.interrupted());

    Thread.currentThread().interrupt();

    ts.awaitTerminalEvent(5, TimeUnit.SECONDS);

    assertTrue(Thread.interrupted());
  }

  @Test
  public void assertTerminated2() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    assertFalse(ts.isTerminated());

    ts.onError(new TestException());
    ts.onError(new IOException());

    assertTrue(ts.isTerminated());
    assertThrows(AssertionError.class, ts::assertTerminated);
    assertThrows(AssertionError.class, () -> ts.assertError(TestException.class));
  }

  @Test
  public void assertTerminated3() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onError(new TestException());
    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertTerminated);
  }

  @Test
  public void onSubscribe() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(null);

    ts.assertError(NullPointerException.class);

    ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    TestHelper.BooleanSubscription d1 = new TestHelper.BooleanSubscription();

    ts.onSubscribe(d1);

    assertTrue(d1.isCancelled());

    ts.assertError(IllegalStateException.class);

    ts = TestSubscriber.create();
    ts.dispose();

    d1 = new TestHelper.BooleanSubscription();

    ts.onSubscribe(d1);

    assertTrue(d1.isCancelled());

  }

  @Test
  public void assertValueSequence() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onNext(1);
    ts.onNext(2);
    assertThrows(AssertionError.class, () -> ts.assertValueSequence(Collections.emptyList()));
    assertThrows(AssertionError.class, () -> ts.assertValueSequence(Collections.singletonList(1)));
    ts.assertValueSequence(Arrays.asList(1, 2));
    assertThrows(AssertionError.class, () -> ts.assertValueSequence(Arrays.asList(1, 2, 3)));
  }

  @Test
  public void assertEmpty() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    assertThrows(AssertionError.class, ts::assertEmpty);

    ts.onSubscribe(new TestHelper.BooleanSubscription());
    ts.assertEmpty();
    ts.onNext(1);
    assertThrows(AssertionError.class, ts::assertEmpty);
  }

  @Test
  public void awaitDoneTimed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Thread.currentThread().interrupt();

    RuntimeException ex = assertThrows(RuntimeException.class, () -> ts.awaitDone(5, TimeUnit.SECONDS));
    assertTrue(ex.getCause() instanceof InterruptedException, ex.toString());
  }

  @Test
  public void assertNotSubscribed() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.assertNotSubscribed();

    ts.errors().add(new TestException());

    assertThrows(AssertionError.class, ts::assertNotSubscribed);
  }

  @Test
  public void assertErrorMultiple() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    TestException e = new TestException();
    ts.errors().add(e);
    ts.errors().add(new TestException());

    assertThrows(AssertionError.class, () -> ts.assertError(TestException.class));
    assertThrows(AssertionError.class, () -> ts.assertError(e));
    assertThrows(AssertionError.class, () -> ts.assertErrorMessage(""));
  }

  @Test
  public void assertComplete() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.onSubscribe(new TestHelper.BooleanSubscription());

    assertThrows(AssertionError.class, ts::assertComplete);

    ts.onComplete();

    ts.assertComplete();

    ts.onComplete();

    assertThrows(AssertionError.class, ts::assertComplete);
  }

  @Test
  public void completeWithoutOnSubscribe() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    ts.onComplete();

    ts.assertError(IllegalStateException.class);
  }

  @Test
  public void completeDelegateThrows() {
    TestSubscriber<Integer> ts = new TestSubscriber<>(new CoreSubscriber<Integer>() {

      @Override
      public void onSubscribe(Subscription d) {

      }

      @Override
      public void onNext(Integer value) {

      }

      @Override
      public void onError(Throwable e) {
        throw new TestException();
      }

      @Override
      public void onComplete() {
        throw new TestException();
      }

    });

    ts.onSubscribe(new TestHelper.BooleanSubscription());
    assertThrows(TestException.class, ts::onComplete);
    assertTrue(ts.isTerminated());
  }

  @Test
  public void errorDelegateThrows() {
    TestSubscriber<Integer> ts = new TestSubscriber<>(new CoreSubscriber<Integer>() {

      @Override
      public void onSubscribe(Subscription d) {

      }

      @Override
      public void onNext(Integer value) {

      }

      @Override
      public void onError(Throwable e) {
        throw new TestException();
      }

      @Override
      public void onComplete() {
        throw new TestException();
      }

    });

    ts.onSubscribe(new TestHelper.BooleanSubscription());
    assertThrows(TestException.class, () -> ts.onError(new IOException()));
    assertTrue(ts.isTerminated());
  }

//
//  @Test
//  public void syncQueueThrows() {
//    TestSubscriber<Object> ts = new TestSubscriber<Object>();
//    ts.setInitialFusionMode(QueueFuseable.SYNC);
//
//    Flux.range(1, 5)
//        .map(new Function<Integer, Object>() {
//          @Override
//          public Object apply(Integer v) throws Exception { throw new TestException(); }
//        })
//        .subscribe(ts);
//
//    ts.assertSubscribed()
//        .assertFuseable()
//        .assertFusionMode(QueueFuseable.SYNC)
//        .assertFailure(TestException.class);
//  }
//
//  @Test
//  public void asyncQueueThrows() {
//    TestSubscriber<Object> ts = new TestSubscriber<Object>();
//    ts.setInitialFusionMode(QueueFuseable.ANY);
//
//    UnicastProcessor<Integer> up = UnicastProcessor.create();
//
//    up
//        .map(new Function<Integer, Object>() {
//          @Override
//          public Object apply(Integer v) throws Exception { throw new TestException(); }
//        })
//        .subscribe(ts);
//
//    up.onNext(1);
//
//    ts.assertSubscribed()
//        .assertFuseable()
//        .assertFusionMode(QueueFuseable.ASYNC)
//        .assertFailure(TestException.class);
//  }

  @Test
  public void assertValuePredicateEmpty() {
    TestSubscriber<Object> ts = new TestSubscriber<>();

    Flux.empty().subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValue(o -> false), "No values");
  }

  @Test
  public void assertValuePredicateMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1).subscribe(ts);

    ts.assertValue(o -> o == 1);
  }

  @Test
  public void assertValuePredicateNoMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValue(o -> o != 1), "Value not present");
  }

  @Test
  public void assertValuePredicateMatchButMore() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValue(o -> o == 1), "Value present but other values as well");
  }

  @Test
  public void assertValueAtPredicateEmpty() {
    TestSubscriber<Object> ts = new TestSubscriber<>();

    Flux.empty().subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(0, o -> false), "No values");
  }

  @Test
  public void assertValueAtEmpty() {
    TestSubscriber<Object> ts = new TestSubscriber<>();

    Flux.empty().subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(0, false), "No values");
  }

  @Test
  public void assertValueAtPredicateMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2).subscribe(ts);

    ts.assertValueAt(1, o -> o == 2);
  }

  @Test
  public void assertValueAtMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2).subscribe(ts);

    ts.assertValueAt(1, 2);
  }

  @Test
  public void assertValueAtPredicateNoMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2, 3).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(2, o -> o != 3), "Value not present");
  }

  @Test
  public void assertValueAtNoMatch() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2, 3).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(2, 1), "Value not present");
  }

  @Test
  public void assertValueAtPredicateInvalidIndex() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(2, o -> o == 1), "Invalid index: 2 (latch = 0, values = 2, errors = 0, completions = 1)");
  }

  @Test
  public void assertValueAtInvalidIndex() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();

    Flux.just(1, 2).subscribe(ts);

    assertThrows(AssertionError.class, () -> ts.assertValueAt(2, 1), "Invalid index: 2 (latch = 0, values = 2, errors = 0, completions = 1)");
  }

  @Test
  public void requestMore() {
    TestSubscriber<Integer> ts = new TestSubscriber<>(0);

    Flux.range(1, 5).subscribe(ts);

    ts.requestMore(1)
        .assertValue(1)
        .requestMore(2)
        .assertValues(1, 2, 3)
        .requestMore(3)
        .assertResult(1, 2, 3, 4, 5);
  }

  @Test
  public void withTag() {
    try {
      for (int i = 1; i < 3; i++) {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Flux.just(i)
            .subscribe(ts);

        ts.withTag("testing with item=" + i)
            .assertResult(1)
        ;
      }
      fail("Should have thrown!");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("testing with item=2"), ex.toString());
    }
  }

  @Test
  public void timeoutIndicated() throws InterruptedException {
    Thread.interrupted(); // clear flag

    TestSubscriber<Integer> ts = new TestSubscriber<>();
    Flux.<Integer>never()
        .subscribe(ts);
    assertFalse(ts.await(1, TimeUnit.MILLISECONDS));
    AssertionError ex = assertThrows(AssertionError.class, () -> ts.assertResult(1));
    assertTrue(ex.toString().contains("timeout!"), ex.toString());
  }

  @Test
  public void timeoutIndicated2() {
    final TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.<Integer>never().subscribe(ts);

    AssertionError ex = assertThrows(AssertionError.class, () ->
        ts.awaitDone(1, TimeUnit.MILLISECONDS).assertResult(1)
    );
    assertTrue(ex.toString().contains("timeout!"), ex.toString());
  }

  @Test
  public void timeoutIndicated3() {
    final TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.<Integer>never().subscribe(ts);

    assertFalse(ts.awaitTerminalEvent(1, TimeUnit.MILLISECONDS));
    AssertionError ex = assertThrows(AssertionError.class, () -> ts.assertResult(1));
    assertTrue(ex.toString().contains("timeout!"), ex.toString());
  }

  @Test
  public void disposeIndicated() {
    TestSubscriber<Object> ts = new TestSubscriber<>();
    ts.cancel();
    Throwable ex = assertThrows(Throwable.class, () -> ts.assertResult(1));
    assertTrue(ex.toString().contains("disposed!"), ex.toString());
  }

  @Test
  public void checkTestWaitStrategyEnum() {
    TestHelper.checkEnum(BaseTestConsumer.TestWaitStrategy.class);
  }

  @Test
  public void awaitCount() {
    final TestSubscriber<Integer> ts = TestSubscriber.create(5);

    Flux.range(1, 10).delayElements(Duration.ofMillis(100))
        .subscribe(ts);

    ts.awaitCount(5)
        .assertValues(1, 2, 3, 4, 5)
        .requestMore(5)
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  @Test
  public void awaitCountLess() {
    final TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.range(1, 4)
        .subscribe(ts);

    ts.awaitCount(5)
        .assertResult(1, 2, 3, 4);
  }

  @Test
  public void awaitCountLess2() {
    final TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.range(1, 4)
        .subscribe(ts);

    ts.awaitCount(5, BaseTestConsumer.TestWaitStrategy.YIELD)
        .assertResult(1, 2, 3, 4);
  }

  @Test
  public void awaitCountLess3() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    Flux.range(1, 4).delayElements(Duration.ofMillis(50))
        .subscribe(ts);

    ts.awaitCount(5, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS)
        .assertResult(1, 2, 3, 4);
  }

  @Test
  public void interruptTestWaitStrategy() {
    Thread.currentThread().interrupt();
    RuntimeException ex = assertThrows(RuntimeException.class, () -> BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS.run());
    assertTrue(ex.getCause() instanceof InterruptedException, ex.toString());
  }

  @Test
  public void awaitCountTimeout() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.<Integer>never()
        .subscribe(ts);

    ts.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 50);

    assertTrue(ts.isTimeout());
    ts.clearTimeout();
    assertFalse(ts.isTimeout());
  }

  @Test
  public void assertTimeout() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    Flux.<Integer>never()
        .subscribe(ts);
    ts.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 50)
        .assertTimeout();
  }

  @Test
  public void assertTimeout2() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    Flux.<Integer>empty()
        .subscribe(ts);

    assertThrows(AssertionError.class, () ->
      ts.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 50)
          .assertTimeout()
    );
  }

  @Test
  public void assertNoTimeout() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.just(1)
        .subscribe(ts);

    ts.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 50)
        .assertNoTimeout();
  }

  @Test
  public void assertNoTimeout2() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    Flux.<Integer>never()
        .subscribe(ts);

    assertThrows(AssertionError.class, () ->
        ts.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 50)
            .assertNoTimeout()
    );
  }

  @Test
  public void assertNeverPredicateThrows() {
    TestSubscriber<Integer> ts = TestSubscriber.create();

    Flux.just(1)
        .subscribe(ts);

    assertThrows(IllegalArgumentException.class, () ->
      ts.assertNever(t -> {
        throw new IllegalArgumentException();
      })
    );
  }

  @Test
  public void assertValueAtPredicateThrows() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    Flux.just(1)
        .subscribe(ts);

    assertThrows(IllegalArgumentException.class, () ->
      ts.assertValueAt(0, t -> {
        throw new IllegalArgumentException();
      })
    );
  }

  @Test
  public void waitStrategyRuns() {
    for (BaseTestConsumer.TestWaitStrategy ws : BaseTestConsumer.TestWaitStrategy.values()) {
      ws.run();
    }
  }

  @Test
  public void assertValuesOnly() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    ts.onSubscribe(new TestHelper.BooleanSubscription());
    ts.assertValuesOnly();

    ts.onNext(5);
    ts.assertValuesOnly(5);

    ts.onNext(-1);
    ts.assertValuesOnly(5, -1);
  }

  @Test
  public void assertValuesOnlyThrowsOnUnexpectedValue() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    ts.onSubscribe(new TestHelper.BooleanSubscription());
    ts.assertValuesOnly();

    ts.onNext(5);
    ts.assertValuesOnly(5);

    ts.onNext(-1);
    assertThrows(AssertionError.class, () -> ts.assertValuesOnly(5));
  }

  @Test
  public void assertValuesOnlyThrowsWhenCompleted() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onComplete();
    assertThrows(AssertionError.class, ts::assertValuesOnly);
  }

  @Test
  public void assertValuesOnlyThrowsWhenErrored() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    ts.onSubscribe(new TestHelper.BooleanSubscription());

    ts.onError(new TestException());
    assertThrows(AssertionError.class, ts::assertValuesOnly);
  }

  @Test
  public void awaitCount0() {
    TestSubscriber<Integer> ts = TestSubscriber.create();
    ts.awaitCount(0, BaseTestConsumer.TestWaitStrategy.SLEEP_1MS, 0);
  }

  @Test
  public void testDeferredRequest() {
    TestSubscriber<Integer> ts = new TestSubscriber<>();
    ts.request(5);
    Flux.just(1, 2, 3, 4, 5).subscribe(ts);
    ts.assertValueCount(5);
    ts.assertComplete();
  }
}
