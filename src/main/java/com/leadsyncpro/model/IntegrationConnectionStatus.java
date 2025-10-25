package com.leadsyncpro.model;

/**
 * Represents the high level state of a third-party integration for an organisation.
 * <p>
 * The enum is intentionally concise so it can be safely surfaced to the frontend
 * without additional mapping logic.
 */
public enum IntegrationConnectionStatus {
    /**
     * The integration completed successfully and is ready for use.
     */
    CONNECTED,

    /**
     * There is an existing configuration but we are currently waiting for
     * an external OAuth callback to finish.
     */
    PENDING,

    /**
     * The integration has never been configured or the configuration was removed.
     */
    DISCONNECTED,

    /**
     * The integration reported a runtime error that needs user attention.
     */
    ERROR,

    /**
     * The access token has expired and requires a manual reconnect.
     */
    EXPIRED
}
