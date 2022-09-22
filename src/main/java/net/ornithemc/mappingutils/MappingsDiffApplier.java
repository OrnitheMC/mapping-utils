package net.ornithemc.mappingutils;

import java.util.Arrays;
import java.util.List;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;

class MappingsDiffApplier {

	static Mappings run(Mappings src, MappingsDiff... diffs) throws Exception {
		return run(src, Arrays.asList(diffs));
	}

	static Mappings run(Mappings src, List<MappingsDiff> diffs) throws Exception {
		return new MappingsDiffApplier(src, diffs).run();
	}

	private final Mappings src;
	private final Mappings dst;
	private final List<MappingsDiff> diffs;

	private MappingsDiffApplier(Mappings src, List<MappingsDiff> diffs) {
		src.validate();

		for (MappingsDiff diff : diffs) {
			diff.validate();
		}

		this.src = src;
		this.dst = this.src.copy();
		this.diffs = diffs;
	}

	private Mappings run() throws Exception {
		for (MappingsDiff diff : diffs) {
			apply(diff);
		}

		return dst;
	}

	private void apply(MappingsDiff diff) {
		for (ClassDiff cd : diff.getClasses()) {
			applyDiff(cd);
		}
	}

	private void applyDiff(Diff<?> d) {
		MappingTarget target = d.target();
		String key = d.key();
		String a = d.get(DiffSide.A);
		String b = d.get(DiffSide.B);

		Mapping<?> m = findMapping(d, false);

		if (MappingsDiff.isDiff(a, b)) {
			if (a.isEmpty()) {
				if (m == null) {
					m = findMapping(d, true);

					if (m == null) {
						System.out.println("ignoring invalid diff " + d + " - unable to add mapping in dst!");
					} else {
						m.set(b);
					}
				} else {
					System.out.println("ignoring invalid diff " + d + " - mapping already exists in dst!");
				}
			} else if (b.isEmpty()) {
				if (m == null) {
					Diff<?> pd = d.getParent();

					if (pd == null || !pd.get(DiffSide.B).isEmpty()) {
						System.out.println("ignoring invalid diff " + d + " - mapping does not exist in dst!");
					} else {
						// fail quietly if parent mapping was also removed by this diff
					}
				} else {
					Mapping<?> parent = m.getParent();

					if (parent == null) {
						if (target != MappingTarget.CLASS) {
							throw new IllegalStateException("unable to remove mapping " + m + " - parent mapping not found!");
						}

						dst.removeClass(key);
					} else {
						parent.removeChild(target, key);
					}

					return; // if a mapping is removed, its children also no longer exist
				}
			} else {
				if (m == null) {
					System.out.println("ignoring invalid diff " + d + " - mapping does not exist in dst!");
				} else {
					m.set(b);
				}
			}
		}

		JavadocDiff jd = d.getJavadoc();

		if (jd.isDiff()) {
			String jdb = jd.get(DiffSide.B);

			if (m == null) {
				if (jdb.isEmpty()) {
					System.out.println("ignoring invalid javadoc diff " + jd + " for diff " + d);
				}
			} else {
				m.setJavadoc(jdb);
			}
		}

		for (Diff<?> c : d.getChildren()) {
			applyDiff(c);
		}
	}

	private Mapping<?> findMapping(Diff<?> diff, boolean orAdd) {
		Mapping<?> m = null;
		Diff<?> parentDiff = diff.getParent();

		if (parentDiff == null) {
			if (diff.target() != MappingTarget.CLASS) {
				throw new IllegalStateException("cannot get mapping of target " + diff.target() + " from the root mappings");
			}

			m = dst.getClass(diff.key());

			if (m == null && orAdd) {
				m = dst.addClass(diff.src(), diff.src());
			}
		} else {
			Mapping<?> parent = findMapping(parentDiff, orAdd);

			if (parent != null) {
				m = parent.getChild(diff.target(), diff.key());

				if (m == null && orAdd) {
					m = parent.addChild(diff.target(), diff.key(), diff.src());
				}
			}
		}

		return m;
	}
}
