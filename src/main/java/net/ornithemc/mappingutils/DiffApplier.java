package net.ornithemc.mappingutils;

import java.util.Arrays;
import java.util.Collection;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;

class DiffApplier {

	static void run(Mappings mappings, MappingsDiff... diffs) throws Exception {
		run(mappings, Arrays.asList(diffs));
	}

	static void run(Mappings mappings, Collection<MappingsDiff> diffs) throws Exception {
		new DiffApplier(mappings, diffs).run();
	}

	private final Mappings mappings;
	private final Collection<MappingsDiff> diffs;

	private DiffApplier(Mappings mappings, Collection<MappingsDiff> diffs) {
		mappings.validate();

		for (MappingsDiff diff : diffs) {
			diff.validate();
		}

		this.mappings = mappings;
		this.diffs = diffs;
	}

	private void run() throws Exception {
		for (MappingsDiff diff : diffs) {
			for (ClassDiff cd : diff.getTopLevelClasses()) {
				applyDiff(cd);
			}
		}
	}

	private void applyDiff(Diff diff) {
		Result result = applyDiff(diff, Operation.of(diff));

		// If a mapping is removed, its children also no longer exist.
		// The diff file most likely still contains information about
		// the removal of the children of this diff, but we do not
		// need to explicitly remove them, as removing the parent diff
		// already took care of that for us.
		if (result.operation() != Operation.REMOVE) {
			for (Diff c : diff.getChildren()) {
				applyDiff(c);
			}
		}
	}

	private Result applyDiff(Diff diff, Operation op) {
		MappingTarget target = diff.target();
		String key = diff.key();
		Mapping m = null;

		String o = diff.get(DiffSide.A);
		String n = diff.get(DiffSide.B);

		Diff parentDiff = diff.getParent();

		if (parentDiff == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings");
			}

			m = mappings.getClass(key);

			if (op == Operation.ADD) {
				if (m == null) {
					m = mappings.addClass(key, n);
				} else {
					System.out.println("ignoring invalid diff " + diff + " - mapping already exists!");
					op = Operation.NONE;
				}
			}
			if (op == Operation.REMOVE) {
				if (m == null) {
					System.out.println("ignoring invalid diff " + diff + " - mapping does not exist!");
					op = Operation.NONE;
				} else {
					m = mappings.removeClass(key);
				}
			}
		} else {
			Result parentResult = applyDiff(parentDiff, Operation.NONE);
			Mapping parent = parentResult.mapping();

			if (parent == null) {
				if (op != Operation.NONE) {
					System.out.println("ignoring invalid diff " + diff + " - parent mapping does not exist! (were the diffs provided in the wrong order?)");
				}
			} else {
				m = parent.getChild(target, key);

				if (op == Operation.ADD) {
					if (m == null) {
						m = parent.addChild(target, key, n);
					} else {
						System.out.println("ignoring invalid diff " + diff + " - mapping already exists!");
						op = Operation.NONE;
					}
				}
				if (op == Operation.REMOVE) {
					if (m == null) {
						System.out.println("ignoring invalid diff " + diff + " - mapping does not exist!");
						op = Operation.NONE;
					} else {
						m = parent.removeChild(target, key);
					}
				}
			}
		}

		if (op == Operation.CHANGE) {
			if (m == null) {
				System.out.println("ignoring invalid diff " + diff + " - mapping does not exist!");
				op = Operation.NONE;
			} else if (m.get().equals(o)) {
				m.set(n);
			} else {
				System.out.println("ignoring invalid diff " + diff + " - mapping does not match!");
				op = Operation.NONE;
			}
		}

		JavadocDiff jdiff = diff.getJavadoc();

		if (jdiff.isDiff()) {
			if (m == null) {
				System.out.println("ignoring invalid diff " + jdiff + " - mapping does not exist!");
			} else {
				m.setJavadoc(jdiff.get(DiffSide.B));
			}
		}

		return new Result(m, op);
	}

	private class Result {

		private final Mapping mapping;
		private final Operation op;

		public Result(Mapping mapping, Operation op) {
			this.mapping = mapping;
			this.op = op;
		}

		public Mapping mapping() {
			return mapping;
		}

		public Operation operation() {
			return op;
		}
	}

	private enum Operation {

		NONE, CHANGE, ADD, REMOVE;

		public static Operation of(Diff d) {
			if (d.isDiff()) {
				String o = d.get(DiffSide.A);
				String n = d.get(DiffSide.B);

				if (o.isEmpty()) {
					return ADD;
				} else if (n.isEmpty()) {
					return REMOVE;
				} else {
					return CHANGE;
				}
			} else {
				return NONE;
			}
		}
	}
}
