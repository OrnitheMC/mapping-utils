package net.ornithemc.mappingutils.io.diff.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import net.ornithemc.mappingutils.FileUtils;
import net.ornithemc.mappingutils.io.Format;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public class VersionGraph {

	private final Format format;
	private final Map<String, Version> versions;

	private Version root;

	private VersionGraph(Format format) {
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

	public void walk(Consumer<Version> versionVisitor, Consumer<Collection<Version>> pathVisitor) {
		walk(root, false, versionVisitor, pathVisitor);
	}

	public void walk(String startVersion, Consumer<Version> versionVisitor, Consumer<Collection<Version>> pathVisitor) {
		walk(startVersion, false, versionVisitor, pathVisitor);
	}

	public void walkToRoot(String startVersion, Consumer<Version> versionVisitor, Consumer<Collection<Version>> pathVisitor) {
		walk(startVersion, true, versionVisitor, pathVisitor);
	}

	private void walk(String startVersion, boolean towardsRoot, Consumer<Version> versionVisitor, Consumer<Collection<Version>> pathVisitor) {
		Version version = versions.get(startVersion);

		if (version == null) {
			throw new IllegalArgumentException("no version " + startVersion + " is present in this graph!");
		} else {
			walk(version, towardsRoot, versionVisitor, pathVisitor);
		}
	}

	private void walk(Version start, boolean towardsRoot, Consumer<Version> versionVisitor, Consumer<Collection<Version>> pathVisitor) {
		Set<Version> visited = new HashSet<>();
		Map<Version, Set<Version>> curr = new LinkedHashMap<>();
		Map<Version, Set<Version>> next = new LinkedHashMap<>();

		curr.put(start, new LinkedHashSet<>());

		while (!curr.isEmpty()) {
			for (Map.Entry<Version, Set<Version>> e : curr.entrySet()) {
				Version v = e.getKey();
				Set<Version> p = e.getValue();
				Set<Version> n = towardsRoot ? v.parents : v.children;

				p.add(v);

				if (visited.add(v)) {
					versionVisitor.accept(v);
				}
				if (n.isEmpty()) {
					pathVisitor.accept(p);
				}

				for (Version nv : n) {
					// this ensures visiting order maintains order between versions
					if (!visited.containsAll(towardsRoot ? nv.children : nv.parents)) {
						continue;
					}

					next.compute(nv, (key, path) -> {
						if (path == null || path.size() > p.size()) {
							if (n.size() > 1) {
								// path used in multiple branches, copy it
								return new LinkedHashSet<>(p);
							} else {
								return p;
							}
						} else {
							return path;
						}
					});
				}
			}

			Map<Version, Set<Version>> tmp = curr;
			curr = next;
			next = tmp;

			next.clear();
		}
	}

	public Collection<Version> getPathFromRoot(String version) {
		// there might be multiple paths, but we want the shortest one
		Queue<Collection<Version>> paths = new PriorityQueue<>((p1, p2) -> {
			return p1.size() - p2.size();
		});
		walkToRoot(version, v -> { }, p -> {
			// path is towards root, so we need to invert it
			Deque<Version> path = new LinkedList<>();

			for (Version v : p) {
				path.addFirst(v);
			}

			paths.add(path);
		});

		if (paths.isEmpty()) {
			return Collections.emptyList();
		}

		return paths.poll();
	}

	public Collection<MappingsDiff> getDiffsFromRoot(String version) throws IOException {
		Collection<Version> path = getPathFromRoot(version);
		Collection<MappingsDiff> diffs = new LinkedList<>();

		Iterator<Version> it = path.iterator();

		Version v = null;
		Version p = null;

		while (it.hasNext()) {
			p = v;
			v = it.next();

			if (p != null) {
				diffs.add(v.getDiff(p));
			}
		}

		return diffs;
	}

	public void write() throws IOException {
		for (Version v : versions.values()) {
			if (v.isRoot()) {
				v.writeMappings();
			} else {
				v.writeDiffs();
			}
		}
	}

	private VersionGraph resolve(Path dir) throws IOException {
		iterateVersions(dir, (parent, version, path) -> {
			if (parent == null) {
				if (root != null) {
					throw new IllegalStateException("multiple roots present: " + root + ", " + version);
				}

				root = addVersion(version);
				root.paths.put(root, path);
			} else {
				Version v = addVersion(version);
				Version p = addVersion(parent);

				v.parents.add(p);
				p.children.add(v);

				v.paths.put(p, path);
			}
		});

		if (root == null) {
			throw new IllegalStateException("version graph does not have a root!");
		}

		// validate graph, populate depth
		walk(v -> { }, p -> {
			int depth = 0;

			for (Version v : p) {
				if (v.depth < 0 || v.depth < depth) {
					v.depth = depth;
				}

				depth++;
			}
		});

		return this;
	}

	private void iterateVersions(Path path, VersionConsumer operation) throws IOException {
		FileUtils.iterate(path, file -> {
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

	private Version addVersion(String version) {
		return versions.computeIfAbsent(version, key -> new Version(version, format));
	}

	public static VersionGraph of(Format format, Path path) throws IOException {
		return new VersionGraph(format).resolve(path);
	}

	@FunctionalInterface
	private interface VersionConsumer {

		void accept(String parent, String version, Path path);

	}
}
