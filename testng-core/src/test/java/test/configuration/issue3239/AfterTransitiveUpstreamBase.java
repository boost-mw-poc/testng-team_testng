package test.configuration.issue3239;

import org.testng.annotations.AfterClass;

class AfterTransitiveUpstreamBase {

  @AfterClass(groups = "g")
  protected final void parentAfter() {}
}
