package io.github.partywith8.epd;

import io.github.partywith8.epd.motion.*;
import java.util.*;

/** Dependency-free deterministic regression suite, runnable by Gradle or javac. */
public final class MotionTest {
    static int checks;
    static void check(boolean yes, String description) {
        checks++;
        if (!yes)
            throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        double maximumSpeed = 0;
        for (var kind : Movement.values()) {
            var e = new MotionEvent(1, kind, 0, 0, kind == Movement.STRESS ? 96 : 48,
                    kind == Movement.MIRAGE           ? .5
                            : kind == Movement.RIPPLE ? 3
                            : kind == Movement.WAVE   ? 5
                                                      : 18,
                    20, 800, 0, false);
            check(e.offset(0, 0, 19) == 0, "no early motion");
            check(e.offset(0, 0, 820) == 0, "exact return to rest");
            if (kind != Movement.STRESS)
                check(e.offset(e.radius(), e.radius(), 300) == 0, "bounded ambient footprint");
            for (int x = -48; x < 48; x += 3)
                for (int z = -48; z < 48; z += 3)
                    for (int t = 20; t < 820; t += 5) {
                        double y = e.offset(x, z, t), next = e.offset(x, z, t + 1);
                        check(Double.isFinite(y), "finite field");
                        maximumSpeed = Math.max(maximumSpeed, Math.abs(next - y));
                    }
            if (kind.formation()) {
                var held = new MotionEvent(2, kind, 0, 0, 48, 18, 20, 800, 0, true);
                check(!held.finished(10_000), "held formation lifetime");
                var field = new MotionField(1, List.of(held), 400);
                check(field.offset(0, 0, 400) == held.offset(0, 0, 400), "continuous reset start");
                check(field.offset(0, 0, 640) == 0, "reset completes");
            }
        }
        check(maximumSpeed < .4, "single-event vertical speed supports riders: " + maximumSpeed);
        check(new MotionField(1, List.of(), -1).offset(1, 1, 1) == 0, "flat initial state");
        try {
            new MotionEvent(1, Movement.WAVE, 0, 0, 500, 1, 0, 200, 0, false);
            throw new AssertionError("radius accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        var e = new MotionEvent(1, Movement.RIDGE, 0, 0, 48, 18, 20, 800, 0, true);
        try {
            new MotionField(1, List.of(e, e), -1);
            throw new AssertionError("duplicate accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        var expiring = new MotionEvent(3, Movement.WAVE, 0, 0, 48, 5, 20, 480, 0, false);
        var expiryField = new MotionField(5, List.of(e, expiring), -1);
        check(expiryField.expire(499) == expiryField, "idle expiry preserves snapshot identity");
        var expired = expiryField.expire(500);
        check(expired.revision() == 6 && expired.events().equals(List.of(e)),
                "only expired events removed");
        check(new MotionField(5, List.of(expiring), 100).expire(900).events().size() == 1,
                "reset retains frozen events");
        stressChecks();
        System.out.println("STM MOTION TEST PASS: " + checks
                + " checks; max single-event speed=" + maximumSpeed);
        fixtures();
    }
    static void stressChecks() {
        check(!Movement.AMBIENT.contains(Movement.STRESS), "stress never selected by ambience");
        double speed = 0;
        for (int view : new int[] {2, 10, 12, 24, 25, 32}) {
            var scene = StressScene.at(-17, 31, view, 32, 100, 60);
            check(scene.x() == -24 && scene.z() == 24, "negative chunk centre");
            var motion = scene.event(5);
            // Every chunk, including all four square corners, must actually move.
            for (int cx = -view; cx <= view; cx++)
                for (int cz = -view; cz <= view; cz++) {
                    int x = scene.x() + cx * 16, z = scene.z() + cz * 16;
                    double maximum = 0;
                    for (int t = 100; t < 1300; t += 11) {
                        double y = motion.offset(x, z, t);
                        maximum = Math.max(maximum, Math.abs(y));
                        speed = Math.max(speed, Math.abs(motion.offset(x, z, t + 1) - y));
                        check(Double.isFinite(y) && Math.abs(y) <= 24,
                                "bounded stress displacement");
                    }
                    check(maximum > 5, "substantial motion in every view chunk");
                }
            check(motion.offset(scene.x(), scene.z(), 99) == 0
                            && motion.offset(scene.x(), scene.z(), 1300) == 0,
                    "stress exact start/end");
            // Walking into a remote view must retain substantial motion without
            // creating another event or changing the wave's phase underfoot.
            for (int[] destination : new int[][] {{3072, -3072}, {-3072, 3072}, {0, 2048}}) {
                double travelMotion = 0;
                for (int t = 101; t < 1300; t += 7) {
                    double y = motion.offset(destination[0], destination[1], t);
                    travelMotion = Math.max(travelMotion, Math.abs(y));
                    speed = Math.max(speed,
                            Math.abs(motion.offset(destination[0], destination[1], t + 1) - y));
                    check(Double.isFinite(y) && Math.abs(y) <= 24, "remote stress bounds");
                }
                check(travelMotion > 5, "stress continues far beyond initial view");
                var remoteReset = new MotionField(1, List.of(motion), 500);
                check(remoteReset.offset(destination[0], destination[1], 500)
                                == motion.offset(destination[0], destination[1], 500),
                        "remote stop starts continuously");
                check(remoteReset.offset(destination[0], destination[1], 740) == 0,
                        "remote stop completes");
            }
            var reset = new MotionField(1, List.of(motion), 500);
            check(reset.offset(scene.x(), scene.z(), 500)
                            == motion.offset(scene.x(), scene.z(), 500),
                    "stress stop continuous");
            check(reset.offset(scene.x(), scene.z(), 740) == 0, "stress stop completes");
        }
        check(speed < .65, "stress stays within rider carry speed: " + speed);
        check(StressScene.at(0, 0, 32, 10, 0, 60).viewChunks() == 10,
                "server limits received chunks");
        check(StressScene.at(0, 0, 32, 25, 0, 60).viewChunks() == 25,
                "25-chunk server view is supported");
        // Radius is only a diagnostic hint for stress. View changes must not
        // change existing columns' motion or re-anchor phase.
        var narrow = StressScene.at(0, 0, 10, 25, 0, 60).event(1);
        var wide = StressScene.at(0, 0, 25, 25, 0, 60).event(1);
        for (int x = -4000; x <= 4000; x += 113)
            check(narrow.offset(x, -x, 300) == wide.offset(x, -x, 300),
                    "view-independent stress phase");
        try {
            new MotionEvent(1, Movement.STRESS, 0, 0, 1025, 24, 0, 1200, 0, false);
            throw new AssertionError("oversize stress accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println("STM STRESS TEST PASS: sampled max speed=" + speed + " blocks/tick");
    }
    static void fixtures() throws Exception {
        var text = new StringBuilder("[");
        var random = new Random(180555);
        for (int n = 0; n < 1536; n++) {
            int x = random.nextInt(200) - 100, z = random.nextInt(200) - 100;
            double tick = random.nextInt(1200) + random.nextDouble();
            var events = new ArrayList<MotionEvent>();
            for (int i = 0; i < 1 + n % 8; i++)
                events.add(new MotionEvent(i + 1, Movement.values()[(n + i) % 8],
                        random.nextInt(64) - 32, random.nextInt(64) - 32, 48 + random.nextInt(65),
                        i % 3 == 0 ? .5 : 18, 20, 800, i % 4, (n + i) % 2 == 0));
            if (n >= 1024) {
                int radius = n % 2 == 0 ? 208 : 560;
                x = random.nextInt(8192) - 4096;
                z = random.nextInt(8192) - 4096;
                tick = random.nextInt(1600) + random.nextDouble();
                events.clear();
                events.add(new MotionEvent(
                        1, Movement.STRESS, -24, 8, radius, 24, 20, 1200, n % 4, false));
            }
            long reset = n % 3 == 0 ? 600 : -1;
            var field = new MotionField(n, events, reset);
            double sample = reset < 0 ? tick : Math.min(tick, reset);
            double[] params = new double[68];
            params[0] = reset < 0 ? 1 : 1 - MotionEvent.ease((tick - reset) / 240);
            params[1] = events.size();
            for (int i = 0; i < events.size(); i++) {
                var e = events.get(i);
                int r = 4 + i * 4, t = 36 + i * 4;
                params[r] = e.x();
                params[r + 1] = e.z();
                params[r + 2] = e.radius();
                params[r + 3] = e.amplitude();
                params[t] = e.kind().ordinal();
                params[t + 1] = sample - e.start();
                params[t + 2] = e.duration();
                params[t + 3] = e.axis() + (e.hold() ? 4 : 0);
            }
            if (n > 0)
                text.append(',');
            text.append("{\"column\":[")
                    .append(x)
                    .append(',')
                    .append(z)
                    .append("],\"height\":")
                    .append(field.offset(x, z, tick))
                    .append(",\"params\":")
                    .append(Arrays.toString(params))
                    .append('}');
        }
        text.append(']');
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/gpu-cases.json"), text);
    }
}
