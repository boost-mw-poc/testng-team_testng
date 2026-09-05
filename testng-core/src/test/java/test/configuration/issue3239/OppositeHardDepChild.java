package test.configuration.issue3239;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OppositeHardDepChild extends OppositeHardDepBase {

  @BeforeClass(groups = "childFirst")
  public void childSetup() {}

  @Test
  public void test() {}
}
