package com.aicoach.event;

import java.util.UUID;

/**
 * Published (synchronously, inside the deleting transaction) when an account is deleted. Observers clean up
 * data they own — e.g. the auth feature revokes refresh tokens — without the user feature depending on them.
 */
public record UserDeletedEvent(UUID userId) {
}
