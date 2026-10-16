package com.cooptest.client.anim;

/** Same meaning as Player Animation Library's FirstPersonMode. */
public enum FirstPersonMode {
    /** Vanilla first-person rendering; animation is not visible to the local player. */
    NONE(false),
    /** Let the game render vanilla hands while the animation plays. */
    VANILLA(true),
    /** Render the player's own third-person model (arms only) in first person. */
    THIRD_PERSON_MODEL(true),
    /** Render nothing in first person. */
    DISABLED(true);

    private final boolean enabled;

    FirstPersonMode(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static final ThreadLocal<Boolean> FIRST_PERSON_PASS = ThreadLocal.withInitial(() -> false);

    public static boolean isFirstPersonPass() {
        return FIRST_PERSON_PASS.get();
    }

    public static void setFirstPersonPass(boolean value) {
        FIRST_PERSON_PASS.set(value);
    }
}
