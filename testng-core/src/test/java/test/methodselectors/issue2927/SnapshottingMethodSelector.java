package test.methodselectors.issue2927;

import java.util.ArrayList;
import java.util.List;
import org.testng.IMethodSelector;
import org.testng.IMethodSelectorContext;
import org.testng.ITestNGMethod;

/**
 * Copies the {@code setTestMethods} argument instead of keeping the live list. A selector that
 * retains the reference would see later {@code add}s and hide GITHUB-2927.
 *
 * <p>Requires that snapshot before {@code includeMethod}, including for configuration methods.
 */
public class SnapshottingMethodSelector implements IMethodSelector {

  private static final List<String> SNAPSHOT = new ArrayList<>();
  private static boolean setCalled;
  private static boolean includeBeforeSet;
  private static boolean allBoundToTestClass;

  public static List<String> snapshot() {
    return List.copyOf(SNAPSHOT);
  }

  public static boolean includeMethodRanBeforeSetTestMethods() {
    return includeBeforeSet;
  }

  public static boolean receivedMethodsHaveTestClass() {
    return allBoundToTestClass;
  }

  public static void reset() {
    SNAPSHOT.clear();
    setCalled = false;
    includeBeforeSet = false;
    allBoundToTestClass = false;
  }

  @Override
  public boolean includeMethod(
      IMethodSelectorContext context, ITestNGMethod method, boolean isTestMethod) {
    if (!setCalled) {
      includeBeforeSet = true;
      return false;
    }
    return true;
  }

  @Override
  public void setTestMethods(List<ITestNGMethod> testMethods) {
    setCalled = true;
    SNAPSHOT.clear();
    allBoundToTestClass = !testMethods.isEmpty();
    for (ITestNGMethod method : testMethods) {
      SNAPSHOT.add(method.getMethodName());
      if (method.getTestClass() == null) {
        allBoundToTestClass = false;
      }
    }
  }
}
