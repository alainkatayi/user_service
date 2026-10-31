package com.mywallet.user.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {

	private final AtomicReference<Instant> current;
	private final ZoneId zone;

	public MutableClock(Instant initial) {
		this.current = new AtomicReference<>(initial);
		this.zone = ZoneOffset.UTC;
	}

	public void set(Instant instant) {
		current.set(instant);
	}

	public void advance(Duration duration) {
		current.updateAndGet(value -> value.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(current.get(), zone);
	}

	@Override
	public Instant instant() {
		return current.get();
	}

}
