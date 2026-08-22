package xianfish.fluentui.client.ui.animation;

public class Animator {
    private float value, from, to;
    private long startTime;
    private int durationMs;
    private Easing easing = Easing.EASE_OUT_CUBIC;
    private boolean running;

    public void animate(float from, float to, int ms, Easing e) {
        this.from = from; this.to = to; this.durationMs = ms;
        this.easing = e; this.value = from;
        this.startTime = System.currentTimeMillis(); this.running = true;
    }

    public void to(float target, int ms, Easing e) { animate(value, target, ms, e); }

    public void set(float v) { value = v; running = false; }
    public float get() { update(); return value; }
    public boolean isRunning() { update(); return running; }

    private void update() {
        if (!running) return;
        float t = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) durationMs);
        value = from + (to - from) * easing.apply(t);
        if (t >= 1f) running = false;
    }
}
