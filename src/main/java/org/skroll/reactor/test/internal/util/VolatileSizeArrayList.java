package org.skroll.reactor.test.internal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.util.annotation.NonNull;

/**
 * Tracks the current underlying array size in a volatile field.
 *
 * @param <T> the element type
 */
public final class VolatileSizeArrayList<T> extends AtomicInteger implements List<T>, RandomAccess {
  private static final long serialVersionUID = -1888001398752178514L;

  final ArrayList<T> list;

  public VolatileSizeArrayList() {
    list = new ArrayList<>();
  }

  public VolatileSizeArrayList(final int initialCapacity) {
    list = new ArrayList<>(initialCapacity);
  }

  @Override
  public int size() {
    return get();
  }

  @Override
  public boolean isEmpty() {
    return get() == 0;
  }

  @Override
  public boolean contains(final Object o) {
    return list.contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return list.iterator();
  }

  @Override
  public Object[] toArray() {
    return list.toArray();
  }

  @Override
  public <E> E[] toArray(@NonNull final E[] a) {
    return list.toArray(a);
  }

  @Override
  public boolean add(final T e) {
    boolean b = list.add(e);
    lazySet(list.size());
    return b;
  }

  @Override
  public void add(final int index, final T element) {
    list.add(index, element);
    lazySet(list.size());
  }

  @Override
  public boolean remove(final Object o) {
    boolean b = list.remove(o);
    lazySet(list.size());
    return b;
  }

  @Override
  public T remove(final int index) {
    T v = list.remove(index);
    lazySet(list.size());
    return v;
  }

  @Override
  public boolean containsAll(@NonNull final Collection<?> c) {
    return list.containsAll(c);
  }

  @Override
  public boolean addAll(@NonNull final Collection<? extends T> c) {
    boolean b = list.addAll(c);
    lazySet(list.size());
    return b;
  }

  @Override
  public boolean addAll(final int index, @NonNull final Collection<? extends T> c) {
    boolean b = list.addAll(index, c);
    lazySet(list.size());
    return b;
  }

  @Override
  public boolean removeAll(@NonNull final Collection<?> c) {
    boolean b = list.removeAll(c);
    lazySet(list.size());
    return b;
  }

  @Override
  public boolean retainAll(@NonNull final Collection<?> c) {
    boolean b = list.retainAll(c);
    lazySet(list.size());
    return b;
  }

  @Override
  public void clear() {
    list.clear();
    lazySet(0);
  }

  @Override
  public T get(final int index) {
    return list.get(index);
  }

  @Override
  public T set(final int index, final T element) {
    return list.set(index, element);
  }

  @Override
  public int indexOf(final Object o) {
    return list.indexOf(o);
  }

  @Override
  public int lastIndexOf(final Object o) {
    return list.lastIndexOf(o);
  }

  @Override
  public ListIterator<T> listIterator() {
    return list.listIterator();
  }

  @Override
  public ListIterator<T> listIterator(final int index) {
    return list.listIterator(index);
  }

  @Override
  public List<T> subList(final int fromIndex, final int toIndex) {
    return list.subList(fromIndex, toIndex);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof VolatileSizeArrayList) {
      return list.equals(((VolatileSizeArrayList<?>) obj).list);
    }
    return list.equals(obj);
  }

  @Override
  public int hashCode() {
    return list.hashCode();
  }

  @Override
  public String toString() {
    return list.toString();
  }
}
