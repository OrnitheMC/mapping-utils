package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;
import net.ornithemc.mappingutils.io.diff.tree.MappingsDiffTree;
import net.ornithemc.mappingutils.io.diff.tree.Version;

class MappingsDiffPropagator {

	static void run(MappingsDiffTree tree, MappingsDiff diff, String version) throws Exception {
		new MappingsDiffPropagator(tree, diff, version).run();
	}

	private final MappingsDiffTree tree;
	private final MappingsDiff diff;
	private final String version;

	private MappingsDiffPropagator(MappingsDiffTree tree, MappingsDiff diff, String version) {
		this.tree = tree;
		this.diff = diff;
		this.version = version;
	}

	private void run() throws Exception {
		Version v = tree.getVersion(version);

		if (v == null) {
			throw new IllegalStateException("mappings for version " + version + " do not exist!");
		}

		for (ClassDiff cd : diff.getClasses()) {
			propagateClassDiff(v, cd);

			for (FieldDiff fd : cd.getFields()) {
				propagateFieldDiff(v, cd, fd);
			}
			for (MethodDiff md : cd.getMethods()) {
				propagateMethodDiff(v, cd, md);

				for (ParameterDiff pd : md.getParameters()) {
					propagateParameterDiff(v, cd, md, pd);
				}
			}
		}

		tree.write();
	}

	private void propagateClassDiff(Version v, ClassDiff cd) throws Exception {
		if (cd.isDiff()) {
			propagateUp(v, cd);

			for (Version c : v.getChildren()) {
				propagateDown(c, cd);
			}
		}
	}

	private void propagateFieldDiff(Version v, ClassDiff cd, FieldDiff fd) throws Exception {
		if (fd.isDiff()) {
			propagateUp(v, cd, fd);

			for (Version c : v.getChildren()) {
				propagateDown(c, cd, fd);
			}
		}
	}

	private void propagateMethodDiff(Version v, ClassDiff cd, MethodDiff md) throws Exception {
		if (md.isDiff()) {
			propagateUp(v, cd, md);

			for (Version c : v.getChildren()) {
				propagateDown(c, cd, md);
			}
		}
	}

	private void propagateParameterDiff(Version v, ClassDiff cd, MethodDiff md, ParameterDiff pd) throws Exception {
		if (pd.isDiff()) {
			propagateUp(v, cd, md, pd);

			for (Version c : v.getChildren()) {
				propagateDown(c, cd, md, pd);
			}
		}
	}

	private void propagateUp(Version v, ClassDiff cd) throws Exception {
		if (v.isRoot()) {
			Mappings mappings = v.getMappings();
			ClassMapping cm = mappings.getClass(cd.src());

			if (cm == null) {
				mappings.addClass(cd.src(), cd.get(DiffSide.B));
			} else {
				cm.set(cd.get(DiffSide.B));
			}
		} else {
			MappingsDiff vdiff = v.getDiff();
			ClassDiff vcd = vdiff.getClass(cd.src());

			if (vcd == null) {
				propagateUp(v.getParent(), cd);
			} else {
				vcd.set(DiffSide.B, cd.get(DiffSide.B));
			}
		}
	}

	private void propagateDown(Version v, ClassDiff cd) throws Exception {
		MappingsDiff vdiff = v.getDiff();
		ClassDiff vcd = vdiff.getClass(cd.src());

		if (vcd == null) {
			for (Version c : v.getChildren()) {
				propagateDown(c, cd);
			}
		} else {
			vcd.set(DiffSide.A, cd.get(DiffSide.A));
		}
	}

	private void propagateUp(Version v, ClassDiff cd, FieldDiff fd) throws Exception {
		if (v.isRoot()) {
			Mappings mappings = v.getMappings();
			ClassMapping cm = mappings.getClass(cd.src());

			if (cm != null) {
				FieldMapping fm = cm.getField(fd.src(), fd.getDesc());

				if (fm == null) {
					cm.addField(fd.src(), fd.get(DiffSide.B), fd.getDesc());
				} else {
					fm.set(fd.get(DiffSide.B));
				}
			}
		} else {
			MappingsDiff vdiff = v.getDiff();
			ClassDiff vcd = vdiff.getClass(cd.src());

			if (vcd != null) {
				FieldDiff vfd = vcd.getField(fd.src(), fd.getDesc());

				if (vfd == null) {
					propagateUp(v.getParent(), cd, fd);
				} else {
					vfd.set(DiffSide.B, fd.get(DiffSide.B));
				}
			}
		}
	}

	private void propagateDown(Version v, ClassDiff cd, FieldDiff fd) throws Exception {
		MappingsDiff vdiff = v.getDiff();
		ClassDiff vcd = vdiff.getClass(cd.src());
		FieldDiff vfd = (vcd == null) ? null : vcd.getField(fd.src(), fd.getDesc());

		if (vfd == null) {
			for (Version c : v.getChildren()) {
				propagateDown(c, cd, fd);
			}
		} else {
			vfd.set(DiffSide.A, fd.get(DiffSide.A));
		}
	}

	private void propagateUp(Version v, ClassDiff cd, MethodDiff md) throws Exception {
		if (v.isRoot()) {
			Mappings mappings = v.getMappings();
			ClassMapping cm = mappings.getClass(cd.src());

			if (cm != null) {
				MethodMapping mm = cm.getMethod(md.src(), md.getDesc());

				if (mm == null) {
					cm.addMethod(md.src(), md.get(DiffSide.B), md.getDesc());
				} else {
					mm.set(md.get(DiffSide.B));
				}
			}
		} else {
			MappingsDiff vdiff = v.getDiff();
			ClassDiff vcd = vdiff.getClass(cd.src());

			if (vcd != null) {
				MethodDiff vmd = vcd.getMethod(md.src(), md.getDesc());

				if (vmd == null) {
					propagateUp(v.getParent(), cd, md);
				} else {
					vmd.set(DiffSide.B, md.get(DiffSide.B));
				}
			}
		}
	}

	private void propagateDown(Version v, ClassDiff cd, MethodDiff md) throws Exception {
		MappingsDiff vdiff = v.getDiff();
		ClassDiff vcd = vdiff.getClass(cd.src());
		MethodDiff vmd = (vcd == null) ? null : vcd.getMethod(md.src(), md.getDesc());

		if (vmd == null) {
			for (Version c : v.getChildren()) {
				propagateDown(c, cd, md);
			}
		} else {
			vmd.set(DiffSide.A, md.get(DiffSide.A));
		}
	}

	private void propagateUp(Version v, ClassDiff cd, MethodDiff md, ParameterDiff pd) throws Exception {
		if (v.isRoot()) {
			Mappings mappings = v.getMappings();
			ClassMapping cm = mappings.getClass(cd.src());

			if (cm != null) {
				MethodMapping mm = cm.getMethod(md.src(), md.getDesc());

				if (mm != null) {
					ParameterMapping pm = mm.getParameter(pd.getIndex());

					if (pm == null) {
						mm.addParameter(pd.src(), pd.get(DiffSide.B), pd.getIndex());
					} else {
						pm.set(md.get(DiffSide.B));
					}
				}
			}
		} else {
			MappingsDiff vdiff = v.getDiff();
			ClassDiff vcd = vdiff.getClass(cd.src());
			
			if (vcd != null) {
				MethodDiff vmd = vcd.getMethod(md.src(), md.getDesc());

				if (vmd != null) {
					ParameterDiff vpd = vmd.getParameter(pd.getIndex());

					if (vpd == null) {
						propagateUp(v.getParent(), cd, md, pd);
					} else {
						vpd.set(DiffSide.B, pd.get(DiffSide.B));
					}
				}
			}
		}
	}

	private void propagateDown(Version v, ClassDiff cd, MethodDiff md, ParameterDiff pd) throws Exception {
		MappingsDiff vdiff = v.getDiff();
		ClassDiff vcd = vdiff.getClass(cd.src());
		MethodDiff vmd = (vcd == null) ? null : vcd.getMethod(md.src(), md.getDesc());
		ParameterDiff vpd = (vmd == null) ? null : vmd.getParameter(pd.getIndex());

		if (vpd == null) {
			for (Version c : v.getChildren()) {
				propagateDown(c, cd, md, pd);
			}
		} else {
			vpd.set(DiffSide.A, pd.get(DiffSide.A));
		}
	}
}
