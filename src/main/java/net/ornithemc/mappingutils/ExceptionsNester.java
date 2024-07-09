package net.ornithemc.mappingutils;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import net.ornithemc.exceptor.io.ClassEntry;
import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.MethodEntry;
import net.ornithemc.nester.nest.Nests;

class ExceptionsNester {

	static ExceptionsFile run(ExceptionsFile exceptions, Nests nests, boolean apply) {
		return new ExceptionsNester(exceptions, nests, apply).run();
	}

	private final ExceptionsFile src;
	private final ExceptionsFile dst;
	private final Mapper translator;

	private ExceptionsNester(ExceptionsFile exceptions, Nests nests, boolean apply) {
		this.src = exceptions;
		this.dst = new ExceptionsFile(new TreeMap<>());
		this.translator = Nester.buildTranslator(nests, apply);
	}

	public ExceptionsFile run() {
		for (ClassEntry ci : src.classes().values()) {
			String clsNameIn = ci.name();
			String clsNameOut = translator.mapClass(clsNameIn);

			ClassEntry co = new ClassEntry(clsNameOut, new TreeMap<>());
			dst.classes().put(co.name(), co);

			for (MethodEntry mi : ci.methods().values()) {
				String mtdNameIn = mi.name();
				String mtdDescIn = mi.descriptor();
				List<String> mtdExcsIn = mi.exceptions();
				String mtdNameOut = translator.mapMethod(clsNameIn, mtdNameIn, mtdDescIn);
				String mtdDescOut = MappingUtils.translateMethodDescriptor(mtdDescIn, translator);
				List<String> mtdExcsOut = new ArrayList<>();
				for (String exc : mtdExcsIn) {
					mtdExcsOut.add(translator.mapClass(exc));
				}

				MethodEntry mo = new MethodEntry(mtdNameOut, mtdDescOut, mtdExcsOut);
				co.methods().put(mo.name() + mo.descriptor(), mo);
			}
		}

		return dst;
	}
}
