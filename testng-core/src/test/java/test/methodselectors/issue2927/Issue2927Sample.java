package test.methodselectors.issue2927;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class Issue2927Sample {

  private static boolean beforeMethodRan;

  public static boolean beforeMethodRan() {
    return beforeMethodRan;
  }

  public static void reset() {
    beforeMethodRan = false;
  }

  @BeforeMethod
  public void prepare() {
    beforeMethodRan = true;
  }

  @Test
  public void alpha() {}

  @Test
  public void beta() {}
}
