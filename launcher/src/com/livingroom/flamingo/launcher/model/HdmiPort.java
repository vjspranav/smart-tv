package com.livingroom.flamingo.launcher.model;

/** One HDMI input as the launcher shows it. */
public final class HdmiPort {
    public enum State { ON, STANDBY, EMPTY }

    public final int port;
    public final String inputId, name;
    public final State state;

    private HdmiPort(int port, String inputId, String name, State state) {
        this.port = port;
        this.inputId = inputId;
        this.name = name;
        this.state = state;
    }

    /**
     * userName (owner's rename) beats the CEC device name, which beats "HDMI n".
     * inputState 0 (TvInputManager.INPUT_STATE_CONNECTED) means signal → ON. Otherwise a CEC device means
     * STANDBY, and no CEC device means EMPTY (this TV reports 1 for both standby and empty ports).
     */
    public static HdmiPort resolve(int port, String inputId, String userName, String cecName, Integer inputState) {
        String name = userName != null && !userName.trim().isEmpty() ? userName
                : cecName != null && !cecName.trim().isEmpty() ? cecName : "HDMI " + port;
        State st = inputState != null && inputState == 0 ? State.ON : cecName != null ? State.STANDBY : State.EMPTY;
        return new HdmiPort(port, inputId, name, st);
    }
}
