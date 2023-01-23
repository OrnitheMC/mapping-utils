package net.ornithemc.mappingutils;

import java.util.LinkedList;
import java.util.List;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;

class DiffGenerator {

	static MappingsDiff run(Mappings a, Mappings b) throws Exception {
		return new DiffGenerator(a, b).run();
	}

	private final MappingsDiff diff;
	private final Mappings a;
	private final Mappings b;

	private final List<MappingPair> mappingPairs;

	private DiffGenerator(Mappings a, Mappings b) {
		if (!a.getSrcNamespace().equals(b.getSrcNamespace())) {
			throw new IllegalArgumentException("src namespaces do not match!");
		}
		if (!a.getDstNamespace().equals(b.getDstNamespace())) {
			throw new IllegalArgumentException("dst namespaces do not match!");
		}

		a.validate();
		b.validate();

		this.diff = new MappingsDiff();
		this.a = a;
		this.b = b;

		this.mappingPairs = new LinkedList<>();
	}

	private MappingsDiff run() throws Exception {
		collectMappingPairs();
		createMappingDiffs();

		return diff;
	}

	private void collectMappingPairs() {
		for (Mapping ma : a.getTopLevelClasses()) {
			addMappingPair(null, ma, null);
		}
		for (Mapping mb : b.getTopLevelClasses()) {
			Mapping ma = a.getClass(mb.src());

			if (ma == null) {
				addMappingPair(null, null, mb);
			}
		}
	}

	private void addMappingPair(MappingPair parent, Mapping a, Mapping b) {
		if (a == null) {
			a = findMapping(DiffSide.A, parent, b.target(), b.key());
		}
		if (b == null) {
			b = findMapping(DiffSide.B, parent, a.target(), a.key());
		}

		MappingPair pair = new MappingPair(a, b);
		mappingPairs.add(pair);

		if (a != null) {
			for (Mapping ca : a.getChildren()) {
				addMappingPair(pair, ca, null);
			}
		}
		if (b != null) {
			for (Mapping cb : b.getChildren()) {
				if (a == null || a.getChild(cb.target(), cb.key()) == null) {
					addMappingPair(pair, null, cb);
				}
			}
		}
	}

	private void createMappingDiffs() {
		for (MappingPair pair : mappingPairs) {
			Mapping a = pair.get(DiffSide.A);
			Mapping b = pair.get(DiffSide.B);

			if (b == null) {
				diff(a, b, DiffMode.A);
			} else if (a == null) {
				diff(a, b, DiffMode.B);
			} else {
				if (MappingsDiff.safeIsDiff(a.get(), b.get())) {
					diff(a, b, DiffMode.AB);
				}
				if (MappingsDiff.safeIsDiff(a.getJavadoc(), b.getJavadoc())) {
					diff(a, b, DiffMode.JAVADOC);
				}
			}
		}
	}

	private void diff(Mapping a, Mapping b, DiffMode mode) {
		mode.run(a, b, addDiff(a == null ? b : a));
	}

	public Mapping findMapping(DiffSide side, MappingPair parent, MappingTarget target, String key) {
		Mapping m = null;

		if (parent == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + target + " from the root mappings");
			}

			m = (side == DiffSide.A) ? a.getClass(key) : b.getClass(key);
		} else {
			Mapping parentMapping = parent.get(side);

			if (parentMapping != null) {
				m = parentMapping.getChild(target, key);
			}
		}

		return m;
	}

	private Diff addDiff(Mapping mapping) {
		MappingTarget target = mapping.target();
		String key = mapping.key();
		Diff d = null;

		Mapping parentMapping = mapping.getParent();

		if (parentMapping == null) {
			if (target != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get diff of target " + target + " from the root diff");
			}

			d = diff.getClass(key);

			if (d == null) {
				d = diff.addClass(key, "", "");
			}
		} else {
			Diff parent = addDiff(parentMapping);

			if (parent == null) {
				throw new IllegalStateException("unable to get diff for " + parentMapping);
			}

			d = parent.getChild(target, key);

			if (d == null) {
				d = parent.addChild(target, key, "", "");
			}
		}

		return d;
	}

	private class MappingPair {

		private final Mapping a;
		private final Mapping b;

		protected MappingPair(Mapping a, Mapping b) {
			this.a = a;
			this.b = b;
		}

		@Override
		public String toString() {
			return a + " == " + b;
		}

		public Mapping get(DiffSide side) {
			return side == DiffSide.A ? a : b;
		}
	}

	private enum DiffMode {

		A() {

			@Override
			public void run(Mapping a, Mapping b, Diff d) {
				d.set(DiffSide.A, a.get());
				d.set(DiffSide.B, "");
				d.getJavadoc().set(DiffSide.A, a.getJavadoc());
				d.getJavadoc().set(DiffSide.B, "");
			}
		},
		B() {

			@Override
			public void run(Mapping a, Mapping b, Diff d) {
				d.set(DiffSide.A, "");
				d.set(DiffSide.B, b.get());
				d.getJavadoc().set(DiffSide.A, "");
				d.getJavadoc().set(DiffSide.B, b.getJavadoc());
			}
		},
		AB() {

			@Override
			public void run(Mapping a, Mapping b, Diff d) {
				d.set(DiffSide.A, a.get());
				d.set(DiffSide.B, b.get());
			}
		},
		JAVADOC() {

			@Override
			public void run(Mapping a, Mapping b, Diff d) {
				d.getJavadoc().set(DiffSide.A, a.getJavadoc());
				d.getJavadoc().set(DiffSide.B, b.getJavadoc());
			}
		};

		public abstract void run(Mapping a, Mapping b, Diff d);

	}
}
