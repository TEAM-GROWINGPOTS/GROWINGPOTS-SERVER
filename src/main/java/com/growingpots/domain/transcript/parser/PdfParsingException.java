package com.growingpots.domain.transcript.parser;

public class PdfParsingException extends RuntimeException {

    public PdfParsingException(String message) {
        super(message);
    }

    public PdfParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
