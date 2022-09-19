package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.Diff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;

class MappingsDiffApplier {

	static void run(Mappings src, Mappings dst, MappingsDiff... diffs) throws Exception {
		new MappingsDiffApplier(src, dst, diffs).run();
	}

	private final Mappings src;
	private final Mappings dst;
	private final MappingsDiff[] diffs;

	private MappingsDiffApplier(Mappings src, Mappings dst, MappingsDiff... diffs) {
		src.validate();

		for (MappingsDiff diff : diffs) {
			diff.validate();
		}

		this.src = src;
		this.dst = dst;
		this.diffs = diffs;
	}

	private void run() throws Exception {
		for (MappingsDiff diff : diffs) {
			applyDiff(diff);
		}
	}

	private void applyDiff(MappingsDiff diff) {
		for (ClassDiff cd : diff.getClasses()) {
			applyClassDiff(cd);
		}
	}

	private void applyClassDiff(ClassDiff cd) {
		String name = cd.src();
		String a = cd.get(DiffSide.A);
		String b = cd.get(DiffSide.B);

		ClassMapping c = dst.getClass(name);

		if (MappingsDiff.isDiff(a, b)) {
			if (a.isEmpty()) {
				if (c == null) {
					c = dst.addClass(name, b);
				} else {
					System.out.println("ignoring invalid diff " + cd + " - class mapping already exists in dst!");
				}
			} else if (a.isEmpty()) {
				if (c == null) {
					System.out.println("ignoring invalid diff " + cd + " - class mapping does not exist in dst!");
				} else {
					dst.removeClass(name);
				}
			} else {
				if (c == null) {
					System.out.println("ignoring invalid diff " + cd + " - class mapping does not exist in dst!");
				} else if (!c.get().equals(a)) {
					System.out.println("ignoring invalid diff " + cd + " - class mapping does not match!");
				} else {
					c.set(b);
				}
			}
		}

		applyJavadocDiff(cd, c);

		for (FieldDiff fd : cd.getFields()) {
			applyFieldDiff(fd, c);
		}
		for (MethodDiff md : cd.getMethods()) {
			applyMethodDiff(md, c);
		}
	}

	private void applyFieldDiff(FieldDiff fd, ClassMapping c) {
		if (c == null) {
			System.out.println("ignoring diff " + fd + " - no parent class mapping found!");
			return;
		}

		String name = fd.src();
		String desc = fd.getDesc();
		String a = fd.get(DiffSide.A);
		String b = fd.get(DiffSide.B);

		FieldMapping f = c.getField(name, desc);

		if (MappingsDiff.isDiff(a, b)) {
			if (a.isEmpty()) {
				if (f == null) {
					f = c.addField(name, b, desc);
				} else {
					System.out.println("ignoring invalid diff " + fd + " - field mapping already exists in dst!");
				}
			} else if (a.isEmpty()) {
				if (f == null) {
					System.out.println("ignoring invalid diff " + fd + " - field mapping does not exist in dst!");
				} else {
					c.removeField(name, desc);
				}
			} else {
				if (f == null) {
					System.out.println("ignoring invalid diff " + fd + " - field mapping does not exist in dst!");
				} else if (!f.get().equals(a)) {
					System.out.println("ignoring invalid diff " + fd + " - field mapping does not match!");
				} else {
					f.set(b);
				}
			}
		}

		applyJavadocDiff(fd, f);
	}

	private void applyMethodDiff(MethodDiff md, ClassMapping c) {
		if (c == null) {
			System.out.println("ignoring diff " + md + " - no parent class mapping found!");
			return;
		}

		String name = md.src();
		String desc = md.getDesc();
		String a = md.get(DiffSide.A);
		String b = md.get(DiffSide.B);

		MethodMapping m = c.getMethod(name, desc);

		if (MappingsDiff.isDiff(a, b)) {
			if (a.isEmpty()) {
				if (m == null) {
					m = c.addMethod(name, b, desc);
				} else {
					System.out.println("ignoring invalid diff " + md + " - method mapping already exists in dst!");
				}
			} else if (a.isEmpty()) {
				if (m == null) {
					System.out.println("ignoring invalid diff " + md + " - method mapping does not exist in dst!");
				} else {
					c.removeMethod(name, desc);
				}
			} else {
				if (m == null) {
					System.out.println("ignoring invalid diff " + md + " - method mapping does not exist in dst!");
				} else if (!m.get().equals(a)) {
					System.out.println("ignoring invalid diff " + md + " - method mapping does not match!");
				} else {
					m.set(b);
				}
			}
		}

		applyJavadocDiff(md, m);

		for (ParameterDiff pd : md.getParameters()) {
			applyParameterDiff(pd, m);
		}
	}

	private void applyParameterDiff(ParameterDiff pd, MethodMapping m) {
		if (m == null) {
			System.out.println("ignoring diff " + pd + " - no parent method mapping found!");
			return;
		}

		String name = pd.src();
		int index = pd.getIndex();
		String a = pd.get(DiffSide.A);
		String b = pd.get(DiffSide.B);

		ParameterMapping p = m.getParameter(index);

		if (MappingsDiff.isDiff(a, b)) {
			if (a.isEmpty()) {
				if (p == null) {
					p = m.addParameter(name, b, index);
				} else {
					System.out.println("ignoring invalid diff " + pd + " - parameter mapping already exists in dst!");
				}
			} else if (a.isEmpty()) {
				if (p == null) {
					System.out.println("ignoring invalid diff " + pd + " - parameter mapping does not exist in dst!");
				} else {
					m.removeParameter(index);
				}
			} else {
				if (p == null) {
					System.out.println("ignoring invalid diff " + pd + " - parameter mapping does not exist in dst!");
				} else if (!p.get().equals(a)) {
					System.out.println("ignoring invalid diff " + pd + " - parameter mapping does not match!");
				} else {
					p.set(b);
				}
			}
		}

		applyJavadocDiff(pd, p);
	}

	private void applyJavadocDiff(Diff<?> d, Mapping<?> m) {
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
	}
}
