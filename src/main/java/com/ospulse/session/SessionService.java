package com.ospulse.session;

/**
 * Read-side seam over the live session tracker. The integration layer
 * implements this; the panel UI and the sync/persistence layers consume it,
 * so neither depends on RuneLite-specific wiring.
 */
public interface SessionService
{
	/**
	 * The most recent snapshot, or {@code null} if no snapshot has been
	 * produced yet (e.g. before login / first tick).
	 */
	SessionSnapshot getLatest();

	/** Registers a listener to be notified on each new snapshot. */
	void addListener(SessionListener listener);

	/** Removes a previously-registered listener. */
	void removeListener(SessionListener listener);
}
