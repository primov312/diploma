// AI-assisted (Claude), 2026; reviewed by the author.
package com.rocketcredit.backend.users;

/**
 * The three kinds of users. Public sign-up only ever creates {@link #CUSTOMER}; sellers and
 * administrators come from the demo seed or from an administrator. The role is never editable
 * through the API.
 */
public enum Role {
    CUSTOMER, SELLER, ADMIN;

    /** The Spring Security authority, e.g. {@code ROLE_SELLER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
