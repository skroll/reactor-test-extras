package org.skroll.reactor.test;

import org.junit.Test;
import org.mockito.InOrder;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static org.mockito.Mockito.*;

public class SubscriptionHelperTest {

  @Test
  @SuppressWarnings("unchecked")
  public void testDeferredRequestImmediate() {
    Object o = mock(Object.class);
    Subscription s = mock(Subscription.class);
    AtomicReferenceFieldUpdater<Object, Subscription> sf = mock(AtomicReferenceFieldUpdater.class);
    AtomicLongFieldUpdater<Object> rf = mock(AtomicLongFieldUpdater.class);

    when(sf.get(o)).thenReturn(s);
    SubscriptionHelper.deferredRequest(sf, rf, o, 1);

    verify(s).request(1);
    verifyNoMoreInteractions(s);

    verify(sf, times(1)).get(o);
    verifyNoMoreInteractions(sf);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeferredRequest() {
    Object o = mock(Object.class);
    Subscription s = mock(Subscription.class);
    AtomicReferenceFieldUpdater<Object, Subscription> sf = mock(AtomicReferenceFieldUpdater.class);
    AtomicLongFieldUpdater<Object> rf = mock(AtomicLongFieldUpdater.class);

    when(sf.get(o))
      .thenReturn(null)
      .thenReturn(s);

    when(rf.get(o))
      .thenReturn(0L);

    when(rf.compareAndSet(o, 0, 1))
      .thenReturn(true);

    when(rf.getAndSet(o, 0))
      .thenReturn(1L);

    SubscriptionHelper.deferredRequest(sf, rf, o, 1);

    InOrder inOrder = inOrder(sf, rf, s);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verify(rf, times(1)).get(o);
    inOrder.verify(rf, times(1)).compareAndSet(o, 0, 1);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verify(s, times(1)).request(1);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeferredRequest2() {
    Object o = mock(Object.class);
    AtomicReferenceFieldUpdater<Object, Subscription> sf = mock(AtomicReferenceFieldUpdater.class);
    AtomicLongFieldUpdater<Object> rf = mock(AtomicLongFieldUpdater.class);

    when(sf.get(o))
      .thenReturn(null);

    when(rf.get(o))
      .thenReturn(0L);

    when(rf.compareAndSet(o, 0, 1))
      .thenReturn(true);

    when(rf.getAndSet(o, 0))
      .thenReturn(1L);

    SubscriptionHelper.deferredRequest(sf, rf, o, 1);
    InOrder inOrder = inOrder(sf, rf);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verify(rf, times(1)).get(o);
    inOrder.verify(rf, times(1)).compareAndSet(o, 0, 1);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeferredRequestInvalid() {
    Object o = mock(Object.class);
    Subscription s = mock(Subscription.class);
    AtomicReferenceFieldUpdater<Object, Subscription> sf = mock(AtomicReferenceFieldUpdater.class);
    AtomicLongFieldUpdater<Object> rf = mock(AtomicLongFieldUpdater.class);

    when(sf.get(o))
      .thenReturn(null)
      .thenReturn(s);

    SubscriptionHelper.deferredRequest(sf, rf, o, 0);
    InOrder inOrder = inOrder(sf, rf);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testDeferredRequestInvalid2() {
    Object o = mock(Object.class);
    Subscription s = mock(Subscription.class);
    AtomicReferenceFieldUpdater<Object, Subscription> sf = mock(AtomicReferenceFieldUpdater.class);
    AtomicLongFieldUpdater<Object> rf = mock(AtomicLongFieldUpdater.class);

    when(sf.get(o))
      .thenReturn(null)
      .thenReturn(s);

    when(rf.get(o))
      .thenReturn(1L);

    when(rf.compareAndSet(o, 1, 2))
      .thenReturn(true);

    when(rf.getAndSet(o, 0))
      .thenReturn(0L);

    SubscriptionHelper.deferredRequest(sf, rf, o, 1);

    InOrder inOrder = inOrder(sf, rf, s);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verify(rf, times(1)).get(o);
    inOrder.verify(rf, times(1)).compareAndSet(o, 1, 2);
    inOrder.verify(sf, times(1)).get(o);
    inOrder.verify(rf, times(1)).getAndSet(o, 0);
    inOrder.verifyNoMoreInteractions();
  }
}
