package net.ornithemc.mappingutils;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

class MappingConcatenator {

	static Mappings run(Mappings... m) {
		if (m.length == 0) {
			return new Mappings();
		}
		if (m.length == 1) {
			return m[0];
		}

		return new MappingConcatenator(m).run();
	}

	private final Mappings[] src;

	private MappingConcatenator(Mappings... src) {
		this.src = src;
	}

	public Mappings run() {
		Mappings dst = src[0].copy();

		for (ClassMapping c : dst.getTopLevelClasses()) {
			concatenateClass(c);
		}

		return dst;
	}

	private void concatenateClass(ClassMapping c) {
		for (int i = 1; i < src.length; i++) {
			ClassMapping ci = src[i].getClass(c.get());

			if (ci != null) {
				c.set(ci.get());
				c.setSimple(ci.getSimple());
				c.setJavadocs(ci.getJavadocs());

				for (FieldMapping f : c.getFields()) {
					FieldMapping fi = ci.getField(f.get(), f.getDesc());

					if (fi != null) {
						f.set(fi.get());
						f.setJavadocs(fi.getJavadocs());
					}
				}
				for (MethodMapping m : c.getMethods()) {
					MethodMapping mi = ci.getMethod(m.get(), m.getDesc());

					if (mi != null) {
						m.set(mi.get());
						m.setJavadocs(mi.getJavadocs());

						for (ParameterMapping p : m.getParameters()) {
							ParameterMapping fi = mi.getParemeter(p.getIndex());

							if (fi != null) {
								p.set(fi.get());
								p.setJavadocs(fi.getJavadocs());
							}
						}
					}
				}
				for (ClassMapping cc : c.getClasses()) {
					concatenateClass(cc);
				}
			}
		}
	}
}
