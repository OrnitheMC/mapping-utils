package net.ornithemc.mappingutils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.graph.Version;
import net.ornithemc.mappingutils.io.diff.graph.VersionGraph;

class ChangePropagator {

	static void run(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) throws Exception {
		new ChangePropagator(options, graph, changes, version).run();
	}

	private final PropagationOptions options;
	private final VersionGraph graph;

	private final Collection<Version> barriers;

	private Map<Version, MappingsDiff> queuedChanges;
	private Map<Version, MappingsDiff> workingChanges;

	private ChangePropagator(PropagationOptions options, VersionGraph graph, MappingsDiff changes, String version) {
		changes.validate();

		this.options = options;
		this.graph = graph;

		this.barriers = new HashSet<>();

		this.queuedChanges = new HashMap<>();
		this.workingChanges = new HashMap<>();

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

	private void run() throws Exception {
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

	private void propagateChanges(Version v, MappingsDiff changes) throws Exception {
		for (Diff change : changes.getTopLevelClasses()) {
			propagateChange(v, change);
		}
	}

	private void propagateChange(Version v, Diff change) throws Exception {
		DiffMode mode = DiffMode.of(change);
		Operation op = Operation.of(change, mode);

		propagateChange(v, change, mode, op);
	}

	private void propagateChange(Version v, Diff change, DiffMode mode, Operation op) throws Exception {
		// we first propagate up to find sources of the mapping,
		// then propagate the change down from there
		propagateChange(v, change, PropagationDirection.UP, mode, op);

		for (Diff childChange : change.getChildren()) {
			propagateChange(v, childChange);
		}
	}

	private void propagateChange(Version v, Diff change, PropagationDirection dir, DiffMode mode, Operation op) throws Exception {
		if (mode == DiffMode.NONE) {
			return;
		}

		Collection<Pair<Version, Result<?>>> results = new LinkedList<>();

		if (v.isRoot()) {
			Result<?> result = applyChange(v, v.getMappings(), change, mode, op);
			results.add(new Pair<>(null, result));
		} else {
			DiffSide side = (dir == PropagationDirection.UP) ? DiffSide.B : DiffSide.A;
			boolean insert = barriers.contains(v);

			for (Version parent : v.getParents()) {
				Result<?> result = applyChange(v, v.getDiff(parent), change, side, mode, op, insert);
				results.add(new Pair<>(parent, result));

				if (result.success() && options.lenient) {
					queueSiblingChange(v, (Diff)result.subject(), change, dir, mode, op);
				}
			}
		}

		for (Pair<Version, Result<?>> pair : results) {
			Version parent = pair.left;
			Result<?> result = pair.right;

			if (result.success()) {
				v.markDirty();

				if (dir == PropagationDirection.UP) {
					// found the source of the mapping, now propagate down
					for (Version c : v.getChildren()) {
						propagateChange(c, change, PropagationDirection.DOWN, result.mode(), result.operation());
					}
				}
			}

			// the part of the change that was not yet applied
			mode = mode.without(result.mode());

			// keep propagating that part in the same direction
			if (dir == PropagationDirection.UP) {
				if (parent != null) {
					propagateChange(parent, change, dir, mode, op);
				}
			} else {
				for (Version c : v.getChildren()) {
					propagateChange(c, change, dir, mode, op);
				}
			}
		}
	}

	private Result<Mapping> applyChange(Version v, Mappings mappings, Diff change, DiffMode mode, Operation op) {
		MappingTarget target = change.target();
		String key = change.key();
		Mapping m = null;

		String o = change.get(DiffSide.A);
		String n = change.get(DiffSide.B);

		Diff parentChange = change.getParent();

		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings of " + v);
			}

			m = mappings.getClass(key);

			if (mode.is(DiffMode.MAPPINGS)) {
				if (op == Operation.ADD) {
					if (m == null) {
						m = mappings.addClass(key, n);
					} else {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
						mode = mode.without(DiffMode.MAPPINGS);
						op = Operation.NONE;
					}
				}
				if (op == Operation.REMOVE) {
					if (m == null) {
						System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
						mode = mode.without(DiffMode.MAPPINGS);
						op = Operation.NONE;
					} else {
						m = mappings.removeClass(key);
					}
				}
			}
		} else {
			Result<Mapping> parentResult = applyChange(v, mappings, parentChange, DiffMode.NONE, Operation.NONE);
			Mapping parent = parentResult.subject();

			if (parent == null) {
				if (op != Operation.NONE) {
//					System.out.println("ignoring invalid change " + change + " to " + v + " - parent mapping does not exist! (were the diffs provided in the wrong order?)");
					mode = DiffMode.NONE;
					op = Operation.NONE;
				}
			} else {
				m = parent.getChild(target, key);

				if (mode.is(DiffMode.MAPPINGS)) {
					if (op == Operation.ADD) {
						if (m == null) {
							m = parent.addChild(target, key, n);
						} else {
							System.out.println("ignoring invalid change " + change + " to " + v + " - mapping already exists!");
							mode = mode.without(DiffMode.MAPPINGS);
							op = Operation.NONE;
						}
					}
					if (op == Operation.REMOVE) {
						if (m == null) {
							System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
							mode = mode.without(DiffMode.MAPPINGS);
							op = Operation.NONE;
						} else {
							m = parent.removeChild(target, key);
						}
					}
				}
			}
		}

		if (mode.is(DiffMode.MAPPINGS) && op == Operation.CHANGE) {
			if (m == null) {
				System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not exist!");
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			} else if (m.get().equals(o)) {
				m.set(n);
			} else {
				System.out.println("ignoring invalid change " + change + " to " + v + " - mapping does not match!");
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			}
		}

		JavadocDiff jchange = change.getJavadoc();

		if (mode.is(DiffMode.JAVADOCS) && jchange.isDiff()) {
			if (m == null) {
				System.out.println("ignoring invalid change " + jchange + " to " + v + " - mapping does not exist!");
				mode = mode.without(DiffMode.JAVADOCS);
			} else {
				m.setJavadoc(jchange.get(DiffSide.B));
			}
		}

		return new Result<>(m, mode, op);
	}

	private Result<Diff> applyChange(Version v, MappingsDiff diff, Diff change, DiffSide side, DiffMode mode, Operation op, boolean insert) {
		MappingTarget target = change.target();
		String key = change.key();
		Diff d = null;

		String o = change.get(DiffSide.A);
		String n = change.get(DiffSide.B);

		Diff parentChange = change.getParent();

		if (parentChange == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff for " + v);
			}

			d = diff.getClass(key);

			if (d == null && insert) {
				d = diff.addClass(key, o, o);
			}
		} else {
			Result<Diff> parentResult = applyChange(v, diff, parentChange, side, DiffMode.NONE, Operation.NONE, insert);
			Diff parent = parentResult.subject();

			if (parent == null) {
				if (op != Operation.NONE && insert) {
					System.out.println("ignoring invalid change " + change + " to " + v + " - parent diff does not exist! (were the diffs provided in the wrong order?)");
					op = Operation.NONE;
				}
			} else {
				d = parent.getChild(target, key);

				if (d == null && insert) {
					d = parent.addChild(target, key, o, o);
				}
			}
		}

		if (mode.is(DiffMode.MAPPINGS)) {
			if (d == null || (!d.isDiff() && !insert) || !change.isDiff()) {
				mode = mode.without(DiffMode.MAPPINGS);
				op = Operation.NONE;
			} else {
				if (!d.isDiff()) {
					d.set(side, o);
					d.set(side.opposite(), o);
				}

				if (d.get(side).equals(o)) {
					d.set(side, n);
				} else {
					System.out.println("ignoring invalid change " + change + " to " + v + " - diff does not match!");
					op = Operation.NONE;
				}
			}
		}

		JavadocDiff jchange = change.getJavadoc();

		if (mode.is(DiffMode.JAVADOCS)) {
			JavadocDiff jd = (d == null) ? null : d.getJavadoc();

			if (jd == null || (!d.isDiff() && !jd.isDiff() && !insert) || !jchange.isDiff()) {
				if (insert) {
					System.out.println("ignoring invalid change " + jchange + " to " + v + " - diff does not exist!");
				}

				mode = mode.without(DiffMode.JAVADOCS);
			} else {
				String jo = jchange.get(DiffSide.A);
				String jn = jchange.get(DiffSide.B);

				if (!jd.isDiff()) {
					jd.set(side, jo);
					jd.set(side.opposite(), jo);
				}

				if (jd.get(side).equals(jo)) {
					jd.set(side, jn);
				} else {
					System.out.println("ignoring invalid change " + jchange + " to " + v + " - diff does not match!");
				}
			}
		}

		return new Result<>(d, mode, op);
	}

	private void queueSiblingChange(Version v, Diff d, Diff change, PropagationDirection dir, DiffMode mode, Operation op) throws Exception {
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
			if (child.target() == target && child.src().equals(name)) {
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
			String dst = sibling.get(s);

			if (dst.isEmpty() == d.get(s).isEmpty()) {
				throw new IllegalStateException("two targets with the same name (" + d + ", " + sibling + ") exist in the same version!");
			}
			if (!dst.isEmpty() && !dst.equals(change.get(DiffSide.A))) {
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

	private Diff queueSiblingChange(Version v, Diff sibling, Diff change, DiffMode mode, Operation op) throws Exception {
		MappingsDiff changes = queuedChanges.computeIfAbsent(v, key -> new MappingsDiff());
		return queueSiblingChange(v, changes, sibling, change, mode, op);
	}

	private Diff queueSiblingChange(Version v, MappingsDiff changes, Diff sibling, Diff change, DiffMode mode, Operation op) throws Exception {
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
				if (mode.is(DiffMode.MAPPINGS)) {
					siblingChange.set(DiffSide.A, change.get(DiffSide.A));
					siblingChange.set(DiffSide.B, change.get(DiffSide.B));
				}
				if (mode.is(DiffMode.JAVADOCS)) {
					JavadocDiff javadocChange = change.getJavadoc();
					JavadocDiff siblingJavadocChange = change.getJavadoc();
					siblingJavadocChange.set(DiffSide.A, javadocChange.get(DiffSide.A));
					siblingJavadocChange.set(DiffSide.B, javadocChange.get(DiffSide.B));
				}
			}
		}

		return siblingChange;
	}

	private enum DiffMode {

		NONE    (0b00),
		MAPPINGS(0b01),
		JAVADOCS(0b10),
		BOTH    (0b11);

		private static final DiffMode[] ALL;

		static {

			DiffMode[] values = values();
			ALL = new DiffMode[values.length];

			for (DiffMode mode : values) {
				ALL[mode.flags] = mode;
			}
		}

		private final int flags;

		private DiffMode(int flags) {
			this.flags = flags;
		}

		public boolean is(DiffMode mode) {
			return this == mode || this == BOTH;
		}

		public DiffMode with(DiffMode mode) {
			return ALL[flags | mode.flags];
		}

		public DiffMode without(DiffMode mode) {
			return ALL[flags & (~mode.flags)];
		}

		public static DiffMode of(Diff diff) {
			DiffMode mode = NONE;

			if (diff.isDiff()) {
				mode = mode.with(DiffMode.MAPPINGS);
			}
			if (diff.getJavadoc().isDiff()) {
				mode = mode.with(DiffMode.JAVADOCS);
			}

			return mode;
		}
	}

	private enum Operation {

		NONE, CHANGE, ADD, REMOVE;

		public static Operation of(Diff diff, DiffMode mode) {
			if (diff.isDiff() && mode.is(DiffMode.MAPPINGS)) {
				if (diff.get(DiffSide.A).isEmpty()) {
					return ADD;
				}
				if (diff.get(DiffSide.B).isEmpty()) {
					return REMOVE;
				}

				return CHANGE;
			} else {
				return NONE;
			}
		}
	}

	private class Result<T> {

		private final T subject;
		private final DiffMode mode;
		private final Operation op;

		public Result(T subject, DiffMode mode, Operation op) {
			this.subject = subject;
			this.mode = mode;
			this.op = op;
		}

		public T subject() {
			return subject;
		}

		public DiffMode mode() {
			return mode;
		}

		public Operation operation() {
			return op;
		}

		public boolean success() {
			return mode != DiffMode.NONE;
		}
	}
}
