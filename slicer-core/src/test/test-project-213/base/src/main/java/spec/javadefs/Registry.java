package spec.javadefs;

import spec.javadefs.tools.Formatter;

public final class Registry {

  private final String name;

  public Registry(String name) {
    this.name = name;
  }

  public String describe() {
    return Formatter.format(name) + Marker.SUFFIX;
  }

  public int width() {
    return name.length();
  }
}
