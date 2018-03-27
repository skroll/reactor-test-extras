package org.skroll.reactor.test.exceptions;

public class TestException extends RuntimeException {
  public TestException() {
    super();
  }

  /**
   * Constructs a TestException with message and cause.
   * @param message the message
   * @param cause the cause
   */
  public TestException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a TestException with a message only.
   * @param message the message
   */
  public TestException(String message) {
    super(message);
  }

  /**
   * Constructs a TestException with a cause only.
   * @param cause the cause
   */
  public TestException(Throwable cause) {
    super(cause);
  }
}
