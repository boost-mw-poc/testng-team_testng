package test.configuration.issue3239;

import org.testng.annotations.AfterSuite;

class AfterSuiteABase {

  @AfterSuite(dependsOnGroups = "childB")
  protected final void baseA() {}
}
