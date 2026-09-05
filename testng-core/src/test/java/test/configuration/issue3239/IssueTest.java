package test.configuration.issue3239;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.TestNGException;
import org.testng.annotations.Test;
import test.InvokedMethodNameListener;
import test.SimpleBaseTest;

public class IssueTest extends SimpleBaseTest {

  @Test(description = "GITHUB-3239")
  public void beforeClassInheritanceSurvivesGroupDependenciesOnBaseMethods() {
    InvokedMethodNameListener listener = run(BeforeClassOrderingSample.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly("zSetup", "ySetup", "thisSetup", "test");
  }

  @Test(description = "GITHUB-2714")
  public void afterMethodInheritanceSurvivesGroupDependenciesOnBaseMethods() {
    InvokedMethodNameListener listener = run(AfterMethodChildSample.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly(
            "beforeMethod", "beforeChildMethod", "testCase", "afterChildMethod", "afterMethod");
  }

  @Test(description = "GITHUB-2714")
  public void afterMethodInheritanceSurvivesGroupsAndDependsOnGroups() {
    InvokedMethodNameListener listener = run(AfterMethodGroupsChildSample.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly(
            "beforeMethod", "beforeChildMethod", "testCase", "afterChildMethod", "afterMethod");
  }

  @Test(description = "GITHUB-2432")
  public void inheritanceEdgeDoesNotCycleWhenAgnosticMethodIsTransitivelyUpstream() {
    InvokedMethodNameListener listener = run(TransitiveUpstreamChild.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly("baseGroup", "childAgnostic", "childGroup", "baseAfterGroup", "test");
  }

  @Test(description = "GITHUB-2432")
  public void twoHierarchiesDoNotFormACycleOnBeforeSuite() {
    InvokedMethodNameListener listener = run(SuiteAChild.class, SuiteBChild.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly(
            "childB", "childBGroup", "baseA", "childA", "childAGroup", "baseB", "testA", "testB");
  }

  @Test(description = "GITHUB-2432")
  public void inheritanceEdgeDoesNotCycleForAfterClass() {
    InvokedMethodNameListener listener = run(AfterTransitiveUpstreamChild.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly("test", "parentAfter", "childDependsOnG", "childAgnostic");
  }

  @Test(description = "GITHUB-2432")
  public void inheritanceEdgeDoesNotCycleOnPureDependsOnGroupsChain() {
    InvokedMethodNameListener listener = run(GroupChainChild.class);

    assertThat(listener.getInvokedMethodNames())
        .containsExactly("groupC", "childAgnostic", "childInC", "groupB", "groupA", "test");
  }

  @Test(
      expectedExceptions = TestNGException.class,
      expectedExceptionsMessageRegExp = ".*depends on nonexistent method doesNotExist")
  public void missingDependsOnMethodsStillFailsFromInheritanceWalk() {
    run(MissingDependsChild.class);
  }
}
