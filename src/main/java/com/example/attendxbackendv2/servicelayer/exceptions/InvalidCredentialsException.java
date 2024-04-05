package com.example.attendxbackendv2.servicelayer.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException(String message) {
        super("Credentials are invalid. Please try again.");
    }
}
