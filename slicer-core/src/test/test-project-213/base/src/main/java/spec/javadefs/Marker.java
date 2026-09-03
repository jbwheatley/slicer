package spec.javadefs;

public interface Marker {

  String SUFFIX = "!";

  static Marker of() {
    return new Marker() {};
  }
}
