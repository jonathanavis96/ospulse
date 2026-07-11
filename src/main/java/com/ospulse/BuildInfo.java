package com.ospulse;

/**
 * Build-time constants.
 *
 * <p>The default committed value ships with the Plugin Hub build, whose name is
 * "OSPulse". A local sideloaded testing build ({@code ./gradlew -Pdev shadowJar})
 * replaces this class from a generated source dir so the plugin name is suffixed
 * " (dev)" and is distinguishable from the live Plugin Hub copy in the client's
 * plugin list (see {@code generateBuildInfo} in build.gradle).
 *
 * <p>It is a plain committed source file (not generated on the Hub build) so the
 * Plugin Hub packager, which compiles only {@code src/main/java} and does not run
 * custom Gradle tasks, resolves {@link #PLUGIN_NAME} at compile time.
 */
public final class BuildInfo
{
	private BuildInfo() {}

	/** Plugin display name: "OSPulse (dev)" for local -Pdev builds, else "OSPulse". */
	public static final String PLUGIN_NAME = "OSPulse";
}
