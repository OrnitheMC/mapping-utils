package net.ornithemc.mappingutils.io.diff.tree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.ornithemc.mappingutils.FileUtils;
import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public class MappingsDiffTree {

	private final Format format;
	private final Map<String, Version> versions;

	private Version root;

	private MappingsDiffTree(Format format) {
		this.format = format;
		this.versions = new HashMap<>();
	}

	public Format getFormat() {
		return format;
	}

	public Version root() {
		return root;
	}

	public Version getVersion(String version) {
		return versions.get(version);
	}

	public boolean hasVersion(String version) {
		return versions.containsKey(version);
	}

	public List<Version> getPathFromRoot(String version) {
		Version v = versions.get(version);

		if (v == null) {
			return Collections.emptyList();
		}

		LinkedList<Version> vs = new LinkedList<>();

		for (; v != null; v = v.getParent()) {
			vs.addFirst(v);
		}

		return vs;
	}

	public List<MappingsDiff> getDiffsFromRoot(String version) throws Exception {
		Version v = versions.get(version);

		if (v == null) {
			return Collections.emptyList();
		}

		List<Version> vs = getPathFromRoot(version);
		List<MappingsDiff> diffs = new LinkedList<>();

		for (Version vv : vs) {
			if (!vv.isRoot()) {
				diffs.add(vv.getDiff());
			}
		}

		return diffs;
	}

	public void write() throws Exception {
		for (Version v : versions.values()) {
			if (v.isRoot()) {
				v.writeMappings();
			} else {
				v.writeDiff();
			}
		}
	}

	private MappingsDiffTree resolve(Path dir) throws IOException {
		iterateVersions(dir, (parent, version, path) -> {
			if (parent == null) {
				if (root != null) {
					throw new IllegalStateException("multiple roots present: " + root.get() + ", " + version);
				}

				root = addVersion(version, path);
			} else {
				Version v = addVersion(version, path);
				Version p = versions.computeIfAbsent(parent, key -> {
					return new Version(parent, format, path); // placeholder
				});

				v.parent = p;
				p.children.add(v);
			}
		});

		if (root == null) {
			throw new IllegalStateException("version tree does not have a root!");
		}
		for (Version v : versions.values()) {
			if (v.root() != root) {
				throw new IllegalStateException("version " + v.get() + " has illegal root " + v.root());
			}
		}

		return this;
	}

	private void iterateVersions(Path path, VersionConsumer operation) throws IOException {
		FileUtils.iterateFiles(path, file -> {
			String fileName = file.getName();

			// root mappings
			if (fileName.endsWith(format.mappingsExtension())) {
				int versionLength = fileName.length() - format.mappingsExtension().length();
				String version = fileName.substring(0, versionLength);

				operation.accept(null, version, file.toPath());
			}
			// diff
			if (fileName.endsWith(format.diffExtension())) {
				int versionsLength = fileName.length() - format.diffExtension().length();
				String rawVersions = fileName.substring(0, versionsLength);

				String[] versions = rawVersions.split("[#]");

				if (versions.length != 2) {
					return;
				}

				String parent = versions[0];
				String version = versions[1];

				operation.accept(parent, version, file.toPath());
			}
		});
	}

	private Version addVersion(String version, Path path) {
		return versions.compute(version, (key, v) -> {
			Version placeholder = v;
			v = new Version(version, format, path);

			if (placeholder != null) {
				for (Version c : placeholder.children) {
					c.parent = v;
					v.children.add(c);
				}
			}

			return v;
		});
	}

	public static MappingsDiffTree of(Format format, Path path) throws IOException {
		return new MappingsDiffTree(format).resolve(path);
	}

	@FunctionalInterface
	private interface VersionConsumer {

		void accept(String parent, String version, Path path);

	}
}
