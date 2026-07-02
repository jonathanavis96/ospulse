package com.ospulse.session;

/**
 * Notified whenever the tracked session produces a fresh {@link SessionSnapshot}.
 *
 * <p>Called on whatever thread the integration layer computes snapshots on
 * (typically the RuneLite client thread). Listeners that touch Swing must
 * marshal to the EDT themselves.
 */
@FunctionalInterface
public interface SessionListener
{
	void onSessionUpdate(SessionSnapshot snapshot);
}
