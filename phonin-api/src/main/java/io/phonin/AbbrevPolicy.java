package io.phonin;

/**
 * Whether initial-only ("abbreviation") matching is active. Only takes effect for systems whose
 * {@link PhoneticSystem#abbreviable()} is true.
 */
public enum AbbrevPolicy {
    OFF,
    INITIALS
}
