package org.skroll.reactor.test;

import org.mockito.Mockito;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.publisher.Operators;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertSame;

public enum TestHelper {
  ;

  @SuppressWarnings("unchecked")
  public static <T> CoreSubscriber<T> mockSubscriber() {
    CoreSubscriber<T> w = Mockito.mock(CoreSubscriber.class);

    Mockito.doAnswer(invocationOnMock -> {
      Subscription s = invocationOnMock.getArgument(0);
      s.request(Long.MAX_VALUE);
      return null;
    }).when(w).onSubscribe(Mockito.any());

    return w;
  }

  @SuppressWarnings("unchecked")

  public static <E extends Enum<E>> void checkEnum(Class<E> enumClass) {

    try {
      Method m = enumClass.getMethod("values");
      m.setAccessible(true);
      Method e = enumClass.getMethod("valueOf", String.class);
      m.setAccessible(true);

      for (Enum<E> o : (Enum<E>[])m.invoke(null)) {
        assertSame(o, e.invoke(null, o.name()));
      }

    } catch (Throwable ex) {

      throw Exceptions.propagate(ex);

    }

  }

  public static class BooleanSubscription extends AtomicBoolean implements Subscription {
    @Override
    public void request(long l) {
      Operators.validate(l);
    }

    @Override
    public void cancel() {
      lazySet(true);
    }

    public boolean isCancelled() {
      return get();
    }
  }
}
