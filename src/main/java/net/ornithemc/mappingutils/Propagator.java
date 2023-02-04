package net.ornithemc.mappingutils;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.graph.Version;
import net.ornithemc.mappingutils.io.diff.graph.VersionGraph;

class Propagator {

	static void run(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) throws IOException {
		new Propagator(options, graph, changes, version).run();
	}

	private static final Mode[] PROPAGATION_MODE_ORDER = { Mode.MAPPINGS, Mode.JAVADOCS };
	// we first propagate up to find sources of the mapping,
	// then propagate the change down from there
	private static final PropagationDirection[] PROPAGATION_DIRECTION_ORDER = { PropagationDirection.UP, PropagationDirection.DOWN };

	private final PropagationOptions options;
	private final VersionGraph graph;

	private final Collection<Version> barriers;

	private Map<Version, MappingsDiff> queuedChanges;
	private Map<Version, MappingsDiff> workingChanges;

	private final Map<PropagationDirection, Queue<Version>> propagation;

	private Propagator(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) {
		changes.validate();

		this.options = options;
		this.graph = graph;

		this.barriers = new HashSet<>();

		this.queuedChanges = new HashMap<>();
		this.workingChanges = new HashMap<>();

		// order ensures each version is tested only once in each direction
		// up = towards root/smaller depth
		// down = away from root/larger depth
		this.propagation = new EnumMap<>(PropagationDirection.class);
		this.propagation.put(PropagationDirection.UP, new PriorityQueue<>((v1, v2) -> {
			return v1.getDepth() - v2.getDepth();
		}));
		this.propagation.put(PropagationDirection.DOWN, new PriorityQueue<>((v1, v2) -> {
			return v2.getDepth() - v1.getDepth();
		}));

		Version v = graph.getVersion(version);

		if (v == null) {
			throw new IllegalStateException("version " + version + " does not appear in the graph!");
		}

		if (!options.dir.up()) {
			barriers.add(v);
		}
		if (!options.dir.down()) {
			barriers.addAll(v.getChildren());
		}

		queuedChanges.put(v, changes);
	}

	private void run() throws IOException {
		while (!queuedChanges.isEmpty()) {
			prepareQueuedChanges();

			for (Entry<Version, MappingsDiff> entry : workingChanges.entrySet()) {
				propagateChanges(entry.getKey(), entry.getValue());
			}
		}

		graph.write();
	}

	private void prepareQueuedChanges() {
		Map<Version, MappingsDiff> old = workingChanges;
		workingChanges = queuedChanges;
		queuedChanges = old;

		queuedChanges.clear();
	}

	private void propagateChanges(Version v, MappingsDiff changes) throws IOException {
		for (Diff change : changes.getTopLevelClasses()) {
			propagateChange(v, change);
		}
	}

	private void propagateChange(Version v, Diff change) throws IOException {
		for (Mode mode : PROPAGATION_MODE_ORDER) {
			Operation op = mode.operation(change);

			if (op != Operation.NONE) {
				propagateChange(v, change, mode, op);
			}
		}

		for (Diff childChange : change.getChildren()) {
			propagateChange(v, childChange);
		}
	}

	private void propagateChange(Version v, Diff change, Mode mode, Operation op) throws IOException {
		propagation.get(PropagationDirection.UP).add(v);

		for (PropagationDirection dir : PROPAGATION_DIRECTION_ORDER) {
			Queue<Version> queue = propagation.get(dir);

			while (!queue.isEmpty()) {
				propagateChange(queue.poll(), change, dir, mode, op);
			}
		}
	}

	private void propagateChange(Version v, Diff change, PropagationDirection dir, Mode mode, Operation op) throws IOException {
		if (v.isRoot()) {
			Mapping m = applyChange(v, v.getMappings(), change, mode, op);

			if (m != null) {
				// success, now propagate in opposite direction
				if (dir == PropagationDirection.UP) {
					propagation.get(PropagationDirection.DOWN).addAll(v.getChildren());
				}

				v.markDirty();
			}
		} else {
			DiffSide side = (dir == PropagationDirection.UP) ? DiffSide.B : DiffSide.A;
			boolean insert = barriers.contains(v);

			for (Version p : v.getParents()) {
				Diff d = applyChange(v, v.getDiff(p), change, side, mode, op, insert);

				if (d == null) {
					// change not applied to this version, propagate further
					if (dir == PropagationDirection.UP) {
						propagation.get(dir).add(p);
					} else {
						propagation.get(dir).addAll(v.getChildren());
					}
				} else {
					// change applied, now propagate in the opposite direction
					if (dir == PropagationDirection.UP) {
						propagation.get(PropagationDirection.DOWN).addAll(v.getChildren());
					}
					if (mode == Mode.MAPPINGS && options.lenient) {
						queueSiblingChange(v, d, change, side, dir, mode, op);
					}

					v.markDirty();
				}
			}
		}
	}

	private Mapping applyChange(Version v, Mappings mappings, Diff change, Mode mode, Operation op) {
		MappingTarget target = change.target();
		String key = change.key();
		String src = change.src();
		Mapping m = null;

		Diff parentChange = change.getParent();
		Mapping parent = null;

		// retrieve parent and check if mapping already exists
		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings of " + v);
			}

			m = mappings.getClass(src);
		} else {
			parent = applyChange(v, mappings, parentChange, mode, Operation.NONE);

			if (parent == null) {
				if (op != Operation.NONE) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent mapping does not exist! (were the diffs provided in the wrong order?)");
					return null;
				}
			} else {
				m = parent.getChild(target, key);
			}
		}

		// now apply the change
		if (mode == Mode.JAVADOCS || op == Operation.NONE) {
			if (m == null) {
				// add dummy mapping
				if (parent == null) {
					m = mappings.addClass(src, "");
				} else {
					m = parent.addChild(target, key, "");
				}
			}
		}
		if (mode == Mode.MAPPINGS) {
			String o = change.get(DiffSide.A);
			String n = change.get(DiffSide.B);

			switch (op) {
			case NONE:
				break;
			case ADD:
				if (m == null) {
					if (parent == null) {
						m = mappings.addClass(src, o);
					} else {
						m = parent.addChild(target, key, o);
					}

					m.set(n);
				} else {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
					m = null;
				}

				break;
			case REMOVE:
				if (m == null) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				} else {
					if (parent == null) {
						m = mappings.removeClass(src);
					} else {
						m = parent.removeChild(target, key);
					}
				}

				break;
			case CHANGE:
				if (m == null) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				} else {
					if (m.get().equals(o)) {
						m.set(n);
					} else {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not match!");
						m = null;
					}
				}

				break;
			default:
				throw new IllegalStateException("unknown operation " + op);
			}
		}
		if (mode == Mode.JAVADOCS) {
			JavadocDiff jchange = change.getJavadoc();
			String o = jchange.get(DiffSide.A);
			String n = jchange.get(DiffSide.B);

			if (m.getJavadoc().equals(o)) {
				m.setJavadoc(n);
			} else {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - javadoc does not match!");
				m = null;
			}
		}

		return m;
	}

	private Diff applyChange(Version v, MappingsDiff diff, Diff change, DiffSide side, Mode mode, Operation op, boolean insert) {
		MappingTarget target = change.target();
		String key = change.key();
		String src = change.src();
		Diff d = null;

		Diff parentChange = change.getParent();
		Diff parent = null;

		// retrieve parent and check if diff already exists
		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff for " + v);
			}

			d = diff.getClass(src);

			if (d == null && insert) {
				d = diff.addClass(src, "", "");
			}
		} else {
			parent = applyChange(v, diff, parentChange, side, mode, Operation.NONE, insert);

			if (parent == null) {
				if (op != Operation.NONE && insert) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent diff does not exist! (were the diffs provided in the wrong order?)");
					return null;
				}
			} else {
				d = parent.getChild(target, key);

				if (d == null && insert) {
					d = parent.addChild(target, key, "", "");
				}
			}
		}

		// now apply the change
		if (mode == Mode.JAVADOCS || op == Operation.NONE) {
			if (d == null) {
				// add dummy diff
				if (parent == null) {
					d = diff.addClass(src, "", "");
				} else {
					d = parent.addChild(target, key, "", "");
				}
			}
		}
		if (mode == Mode.MAPPINGS) {
			if (op == Operation.NONE) {
				return d;
			}
			if (d == null || (!d.isDiff() && !insert)) {
				return null;
			}

			String o = change.get(DiffSide.A);
			String n = change.get(DiffSide.B);

			if (!d.isDiff()) {
				// might be dummy
				d.set(side, o);
				d.set(side.opposite(), o);
			}
			if (d.get(side).equals(o)) {
				d.set(side, n);
			} else {
				System.out.println("ignoring invalid change " + change + " to " + v + " - diff does not match!");
			}
		}
		if (mode == Mode.JAVADOCS) {
			JavadocDiff jchange = change.getJavadoc();
			JavadocDiff jd = (d == null) ? null : d.getJavadoc();

			if (op == Operation.NONE) {
				return d;
			}
			if (jd == null || (!jd.isDiff() && !insert)) {
				return null;
			}

			String o = jchange.get(DiffSide.A);
			String n = jchange.get(DiffSide.B);

			if (!jd.isDiff()) {
				// might be dummy
				jd.set(side, o);
				jd.set(side.opposite(), o);
			}
			if (jd.get(side).equals(o)) {
				jd.set(side, n);
			} else {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - diff does not match!");
				d = null;
			}
		}

		return d;
	}

	private void queueSiblingChange(Version v, Diff d, Diff change, DiffSide side, PropagationDirection dir, Mode mode, Operation op) {
		MappingTarget target = d.target();
		String name = d.src();

		if (target != MappingTarget.FIELD && target != MappingTarget.METHOD) {
			return;
		}

		Diff parent = d.getParent();
		Diff sibling = null;

		for (Diff child : parent.getChildren()) {
			if (child == d) {
				continue;
			}
			if (child.target() == target && child.src().equals(name) && child.isDiff()) {
				if (sibling == null) {
					sibling = child;
				} else {
					throw new IllegalStateException("found multiple siblings (" + sibling + ", " + child + ") of target " + d + " with the same name!");
				}
			}
		}

		if (sibling == null) {
			return;
		}

		for (DiffSide s : DiffSide.values()) {
			// for the side that the change was applied to,
			// we need to check against the value before the change
			String dst = (s == side) ? change.get(DiffSide.A) : d.get(s);
			String siblingDst = sibling.get(s);

			if (dst.isEmpty() == siblingDst.isEmpty()) {
				throw new IllegalStateException("two targets with the same name (" + d + ", " + sibling + ") exist in " + v + "!");
			}
			if (s != side && !dst.equals(change.get(DiffSide.A))) {
				// diff does not match, do not propagate to this sibling
				return;
			}
		}

		if (dir == PropagationDirection.UP) {
			if (!barriers.contains(v)) {
				for (Version p : v.getParents()) {
					queueSiblingChange(p, sibling, change, mode, op);
				}
			}
		} else {
			queueSiblingChange(v, sibling, change, mode, op);
		}
	}

	private Diff queueSiblingChange(Version v, Diff sibling, Diff change, Mode mode, Operation op) {
		MappingsDiff changes = queuedChanges.computeIfAbsent(v, key -> new MappingsDiff());
		return queueSiblingChange(v, changes, sibling, change, mode, op);
	}

	private Diff queueSiblingChange(Version v, MappingsDiff changes, Diff sibling, Diff change, Mode mode, Operation op) {
		MappingTarget target = change.target();
		String name = change.src();
		Diff siblingChange = null;

		Diff parentChange = change.getParent();

		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff for " + v);
			}

			siblingChange = changes.getClass(name);

			if (siblingChange == null) {
				siblingChange = changes.addClass(name, "", "");
			}
		} else {
			Diff siblingParentChange = queueSiblingChange(v, changes, sibling.getParent(), parentChange, mode, Operation.NONE);
			siblingChange = siblingParentChange.getChild(sibling.target(), sibling.key());

			if (siblingChange == null) {
				siblingChange = siblingParentChange.addChild(sibling.target(), sibling.key(), "", "");
			}

			if (op != Operation.NONE) {
				if (mode == Mode.MAPPINGS) {
					siblingChange.set(DiffSide.A, change.get(DiffSide.A));
					siblingChange.set(DiffSide.B, change.get(DiffSide.B));
				}
				if (mode == Mode.JAVADOCS) {
					JavadocDiff javadocChange = change.getJavadoc();
					JavadocDiff siblingJavadocChange = change.getJavadoc();
					siblingJavadocChange.set(DiffSide.A, javadocChange.get(DiffSide.A));
					siblingJavadocChange.set(DiffSide.B, javadocChange.get(DiffSide.B));
				}
			}
		}

		return siblingChange;
	}

	private enum Mode {

		MAPPINGS {

			@Override
			public Operation operation(Diff change) {
				return Operation.of(change.get(DiffSide.A), change.get(DiffSide.B));
			}
		},
		JAVADOCS {

			@Override
			public Operation operation(Diff change) {
				return Operation.of(change.getJavadoc().get(DiffSide.A), change.getJavadoc().get(DiffSide.B));
			}
		};

		public Operation operation(Diff change) {
			return Operation.NONE;
		}
	}

	private enum Operation {

		NONE, CHANGE, ADD, REMOVE;

		public static Operation of(String a, String b) {
			if (MappingsDiff.safeIsDiff(a, b)) {
				if (a.isEmpty()) {
					return ADD;
				}
				if (b.isEmpty()) {
					return REMOVE;
				}

				return CHANGE;
			}

			return NONE;
		}
	}
}
