package org.testng.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.testng.TestNG;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * Cache-semantics coverage for {@link ClassHelper#getAvailableMethods(Class)} beyond the Method
 * identity check in {@link ClassHelperTest}. GITHUB-3437 is about repeated hierarchy scans on the
 * hot paths, especially {@code Parameters.findDataProvider}, so these tests pin call counts and the
 * edges the cache introduces.
 */
public class ClassHelperCacheTest {

  private static final int LOOKUPS = 200;
  private static final int FACTORY_INSTANCES = 100;

  static class SuperType {
    public void shared() {}
  }

  static class ChildA extends SuperType {
    public void onlyA() {}
  }

  static class ChildB extends SuperType {
    public void onlyB() {}
  }

  public static class RowTest {
    static final AtomicInteger invocations = new AtomicInteger();
    private final int n;

    public RowTest(int n) {
      this.n = n;
    }

    @DataProvider
    public Object[][] rows() {
      return new Object[][] {{n}};
    }

    @Test(dataProvider = "rows")
    public void one(int value) {
      invocations.incrementAndGet();
      assertThat(value).isEqualTo(n);
    }
  }

  public static class RowFactory {
    @Factory
    public Object[] create() {
      Object[] out = new Object[FACTORY_INSTANCES];
      for (int i = 0; i < FACTORY_INSTANCES; i++) {
        out[i] = new RowTest(i);
      }
      return out;
    }
  }

  @Test
  public void nullAndObjectLookupsAreEmptyAndDoNotScan() {
    int before = ClassHelper.availableMethodsComputes.get();

    Set<Method> fromNull = ClassHelper.getAvailableMethods(null);
    Set<Method> fromObject = ClassHelper.getAvailableMethods(Object.class);

    assertThat(fromNull).isEmpty();
    assertThat(fromObject).isEmpty();
    fromNull.clear();
    fromObject.clear();
    assertThat(ClassHelper.getAvailableMethods(null)).isEmpty();
    assertThat(ClassHelper.getAvailableMethods(Object.class)).isEmpty();
    assertThat(ClassHelper.availableMethodsComputes.get()).isEqualTo(before);
  }

  @Test
  public void cacheEntriesAreIsolatedBetweenSubclassesSharingASuperclass() {
    Set<Method> fromA = ClassHelper.getAvailableMethods(ChildA.class);
    Set<Method> fromB = ClassHelper.getAvailableMethods(ChildB.class);

    assertThat(names(fromA)).contains("shared", "onlyA").doesNotContain("onlyB");
    assertThat(names(fromB)).contains("shared", "onlyB").doesNotContain("onlyA");

    Method sharedFromA = named(fromA, "shared");
    Method sharedFromB = named(fromB, "shared");
    assertThat(sharedFromA.getDeclaringClass()).isEqualTo(SuperType.class);
    assertThat(sharedFromB.getDeclaringClass()).isEqualTo(SuperType.class);

    Map<String, Method> firstA = byKey(fromA);
    Map<String, Method> secondA = byKey(ClassHelper.getAvailableMethods(ChildA.class));
    firstA.forEach((key, method) -> assertThat(secondA.get(key)).isSameAs(method));
  }

  @Test
  public void concurrentLookupsReuseTheSameHandles() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch startTogether = new CountDownLatch(1);
    try {
      List<Future<Set<Method>>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        Callable<Set<Method>> task =
            () -> {
              startTogether.await();
              return ClassHelper.getAvailableMethods(ChildA.class);
            };
        results.add(pool.submit(task));
      }
      startTogether.countDown();

      Map<String, Method> first = byKey(results.get(0).get());
      for (Future<Set<Method>> result : results) {
        Map<String, Method> next = byKey(result.get());
        assertThat(next.keySet()).isEqualTo(first.keySet());
        first.forEach((key, method) -> assertThat(next.get(key)).isSameAs(method));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void repeatedLookupsDoNotRescanTheHierarchy() {
    ClassHelper.getAvailableMethods(ChildB.class);
    int before = ClassHelper.availableMethodsComputes.get();

    for (int i = 0; i < LOOKUPS; i++) {
      assertThat(ClassHelper.getAvailableMethods(ChildB.class)).isNotEmpty();
    }

    assertThat(ClassHelper.availableMethodsComputes.get() - before)
        .as("repeated getAvailableMethods calls must be cache hits")
        .isZero();
  }

  @Test
  public void repeatedDataProviderInvocationsDoNotRescanTheTestClass() {
    RowTest.invocations.set(0);
    ClassHelper.getAvailableMethods(RowTest.class);
    int before = ClassHelper.availableMethodsComputes.get();

    TestNG tng = new TestNG(false);
    tng.setTestClasses(new Class[] {RowFactory.class});
    tng.setVerbose(0);
    tng.run();

    assertThat(tng.getStatus()).isZero();
    assertThat(RowTest.invocations.get()).isEqualTo(FACTORY_INSTANCES);
    assertThat(ClassHelper.availableMethodsComputes.get() - before)
        .as(
            "after warming RowTest, findDataProvider must not scan it once per factory instance;"
                + " leftover computes are other classes the nested run sees for the first time")
        .isLessThan(FACTORY_INSTANCES);
  }

  private static Set<String> names(Set<Method> methods) {
    return methods.stream()
        .map(Method::getName)
        .filter(name -> !"$jacocoInit".equals(name))
        .collect(Collectors.toSet());
  }

  private static Method named(Set<Method> methods, String name) {
    return methods.stream()
        .filter(method -> name.equals(method.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing method " + name));
  }

  private static Map<String, Method> byKey(Set<Method> methods) {
    return methods.stream()
        .filter(method -> !"$jacocoInit".equals(method.getName()))
        .collect(
            Collectors.toMap(
                method ->
                    method.getDeclaringClass().getName()
                        + "."
                        + method.getName()
                        + java.util.Arrays.toString(method.getParameterTypes()),
                method -> method));
  }
}
