package spec.javadefs;

public enum Status {
  OPEN,
  CLOSED;

  public String label() {
    return name().toLowerCase();
  }
}
