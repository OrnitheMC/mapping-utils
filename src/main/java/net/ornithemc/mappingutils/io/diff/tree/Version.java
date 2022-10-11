package net.ornithemc.mappingutils.io.diff.tree;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.naming.OperationNotSupportedException;

import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public class Version {

	private final String version;
	private final Format format;
	private final Path path;

	Version parent;
	Set<Version> children;

	private Mappings mappings;
	private MappingsDiff diff;

	private boolean dirty;

	Version(String version, Format format, Path path) {
		this.version = version;
		this.format = format;
		this.path = path;

		this.children = new LinkedHashSet<>();
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

	public String get() {
		return version;
	}

	public Format getFormat() {
		return format;
	}

	public Path getPath() {
		return path;
	}

	public Version getParent() {
		return parent;
	}

	public Version root() {
		return isRoot() ? this : parent.root();
	}

	public boolean isRoot() {
		return parent == null;
	}

	public Collection<Version> getChildren() {
		return Collections.unmodifiableSet(children);
	}

	public Mappings getMappings() throws Exception {
		if (!isRoot()) {
			throw new OperationNotSupportedException("only the root has mappings!");
		}
		if (mappings == null) {
			mappings = format.readMappings(path);
		}

		return mappings;
	}

	public MappingsDiff getDiff() throws Exception {
		if (isRoot()) {
			throw new OperationNotSupportedException("the root does not have a diff!");
		}
		if (diff == null) {
			diff = format.readDiff(path);
		}

		return diff;
	}

	public void writeMappings() throws Exception {
		if (!isRoot()) {
			throw new OperationNotSupportedException("only the root has mappings!");
		}
		if (mappings != null && dirty) {
			format.writeMappings(path, mappings);
		}
	}

	public void writeDiff() throws Exception {
		if (isRoot()) {
			throw new OperationNotSupportedException("the root does not have a diff!");
		}
		if (diff != null && dirty) {
			format.writeDiff(path, diff);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markDirty() {
		dirty = true;
	}
}
