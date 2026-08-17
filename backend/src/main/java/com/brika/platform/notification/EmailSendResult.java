package com.brika.platform.notification;

/** Outcome of one EMAIL send attempt (D8-2: structural worker, no provider approved yet). */
public record EmailSendResult(boolean sent, String providerReference, String failureReason) {}
