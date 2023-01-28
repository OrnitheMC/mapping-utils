package net.ornithemc.mappingutils.io.diff.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public class Version {

	private final String version;
	private final Format format;

	final Set<Version> parents;
	final Set<Version> children;

	private Mappings mappings;
	private final Map<Version, MappingsDiff> diffs;
	final Map<Version, Path> paths;

	private boolean dirty;

	Version(String version, Format format) {
		this.version = version;
		this.format = format;

		this.parents = new LinkedHashSet<>();
		this.children = new LinkedHashSet<>();

		this.diffs = new HashMap<>();
		this.paths = new HashMap<>();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Version)) {
			return false;
		}

		return version.equals(((Version)obj).version);
	}

	@Override
	public int hashCode() {
		return version.hashCode();
	}

	@Override
	public String toString() {
		return version;
	}

	public Format getFormat() {
		return format;
	}

	public Collection<Version> getParents() {
		return Collections.unmodifiableSet(parents);
	}

	public Collection<Version> getChildren() {
		return Collections.unmodifiableSet(children);
	}

	public boolean isRoot() {
		return parents.isEmpty();
	}

	public Mappings getMappings() throws IOException {
		if (!isRoot()) {
			throw new UnsupportedOperationException("only a root has mappings!");
		}
		if (mappings == null) {
			mappings = format.readMappings(paths.get(this));
		}

		return mappings;
	}

	public MappingsDiff getDiff(Version parent) throws IOException {
		if (isRoot()) {
			throw new UnsupportedOperationException("a root does not have any diffs!");
		}

		MappingsDiff diff = diffs.get(parent);

		if (diff == null) {
			diffs.put(parent, diff = format.readDiff(paths.get(parent)));
		}

		return diff;
	}

	public void writeMappings() throws IOException {
		if (!isRoot()) {
			throw new UnsupportedOperationException("only a root has mappings!");
		}
		if (mappings != null && dirty) {
			format.writeMappings(paths.get(this), mappings);
		}
	}

	public void writeDiffs() throws IOException {
		if (isRoot()) {
			throw new UnsupportedOperationException("a root does not have any diffs!");
		}
		if (dirty) {
			for (Version version : diffs.keySet()) {
				MappingsDiff diff = diffs.get(version);
				Path path = paths.get(version);

				format.writeDiff(path, diff);
			}
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markDirty() {
		dirty = true;
	}
}
