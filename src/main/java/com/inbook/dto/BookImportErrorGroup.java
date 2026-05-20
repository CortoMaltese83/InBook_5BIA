package com.inbook.dto;

public record BookImportErrorGroup(String status, String fallbackStep, String reason, long count) {
}
