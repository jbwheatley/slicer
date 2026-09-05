package spec.javadefs;

import spec.javadefs.tools.Formatter;

public final class Outer {

  public static final class Inner {

    private final String label;

    public Inner(String label) {
      this.label = label;
    }

    public String describe() {
      return Formatter.format(label);
    }
  }

  public Inner makeInner(String label) {
    return new Inner(label);
  }
}
