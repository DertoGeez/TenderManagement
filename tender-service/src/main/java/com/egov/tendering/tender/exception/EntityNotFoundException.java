package com.egov.tendering.tender.exception;

public class EntityNotFoundException extends RuntimeException {

  public EntityNotFoundException(String message) {
    super(message);
  }

  public EntityNotFoundException(Throwable throwable) {
    super(throwable);
  }

  public EntityNotFoundException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
