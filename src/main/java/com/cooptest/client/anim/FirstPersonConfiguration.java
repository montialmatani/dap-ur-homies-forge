package com.cooptest.client.anim;

public class FirstPersonConfiguration {
    public static final FirstPersonConfiguration DEFAULT = new FirstPersonConfiguration();

    private boolean showRightArm = false;
    private boolean showLeftArm = false;
    private boolean showRightItem = true;
    private boolean showLeftItem = true;
    private boolean showArmor = false;

    public FirstPersonConfiguration() {}

    public FirstPersonConfiguration(boolean showRightArm, boolean showLeftArm, boolean showRightItem, boolean showLeftItem) {
        this.showRightArm = showRightArm;
        this.showLeftArm = showLeftArm;
        this.showRightItem = showRightItem;
        this.showLeftItem = showLeftItem;
    }

    public FirstPersonConfiguration(boolean showRightArm, boolean showLeftArm, boolean showRightItem, boolean showLeftItem, boolean showArmor) {
        this(showRightArm, showLeftArm, showRightItem, showLeftItem);
        this.showArmor = showArmor;
    }

    public boolean isShowRightArm() { return showRightArm; }
    public boolean isShowLeftArm() { return showLeftArm; }
    public boolean isShowRightItem() { return showRightItem; }
    public boolean isShowLeftItem() { return showLeftItem; }
    public boolean isShowArmor() { return showArmor; }
}
