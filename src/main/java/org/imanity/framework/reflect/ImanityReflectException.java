package org.imanity.framework.reflect;

public class ImanityReflectException extends RuntimeException {

    public ImanityReflectException(Throwable throwable) {
        super(throwable);
    }

    public ImanityReflectException(String message) {
        super(message);
    }

    public ImanityReflectException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
