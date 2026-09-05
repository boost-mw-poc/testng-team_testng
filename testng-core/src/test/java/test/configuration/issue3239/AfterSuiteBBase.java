package test.configuration.issue3239;

import org.testng.annotations.AfterSuite;

class AfterSuiteBBase {

  @AfterSuite(dependsOnGroups = "childA")
  protected final void baseB() {}
}
