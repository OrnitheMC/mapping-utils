package net.ornithemc.mappingutils.io.diff.tree;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;

public class MappingHistory {

	public static MappingHistory of(Mapping m) {
		return m == null ? null : new MappingHistory(of(m.getParent()), m.target(), m.key());
	}

	public static MappingHistory of(Diff d) {
		return d == null ? null : new MappingHistory(of(d.getParent()), d.target(), d.key());
	}

	private final MappingHistory parent;
	private final MappingTarget target;
	private final String key;

	private final Map<Version, Mapping> mappings;
	private final Map<Version, Diff> diffs;

	public MappingHistory(MappingTarget target, String key) {
		this(null, target, key);
	}

	public MappingHistory(MappingHistory parent, MappingTarget target, String key) {
		this.parent = parent;
		this.target = target;
		this.key = key;

		this.mappings = new LinkedHashMap<>();
		this.diffs = new LinkedHashMap<>();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MappingHistory)) {
			return false;
		}

		MappingHistory history = (MappingHistory)obj;
		return target == history.target && key.equals(history.key) && Objects.equals(parent, history.parent);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	public MappingHistory getParent() {
		return parent;
	}

	public MappingTarget getTarget() {
		return target;
	}

	public String getKey() {
		return key;
	}

	public Map<Version, Mapping> getMappings() {
		return Collections.unmodifiableMap(mappings);
	}

	public Map<Version, Diff> getDiffs() {
		return Collections.unmodifiableMap(diffs);
	}

	public void setMapping(Version v, Mapping m) {
		if (checkValid(m)) {
			mappings.put(v, m);
		}
	}

	public void setDiff(Version v, Diff d) {
		if (checkValid(d)) {
			diffs.put(v, d);
		}
	}

	private boolean checkValid(Mapping m) {
		if (m.target() != target) {
			return false;
		}
		if (!m.key().equals(key)) {
			return false;
		}

		return true;
	}

	private boolean checkValid(Diff d) {
		if (d.target() != target) {
			return false;
		}
		if (!d.key().equals(key)) {
			return false;
		}

		return true;
	}
}
