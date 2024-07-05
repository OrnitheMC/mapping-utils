package net.ornithemc.mappingutils;

import java.util.ArrayList;
import java.util.List;

import net.ornithemc.exceptor.io.ClassEntry;
import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.MethodEntry;
import net.ornithemc.mappingutils.io.Mappings;

class ExceptionsMapper {

	static ExceptionsFile run(ExceptionsFile sigs, Mappings mappings) {
		return new ExceptionsMapper(sigs, mappings).run();
	}

	private final ExceptionsFile exceptionsIn;
	private final ExceptionsFile exceptionsOut;
	private final Mapper mapper;

	private ExceptionsMapper(ExceptionsFile sigs, Mappings mappings) {
		this.exceptionsIn = sigs;
		this.exceptionsOut = new ExceptionsFile();
		this.mapper = Mapper.of(mappings);
	}

	private ExceptionsFile run() {
		for (ClassEntry ci : exceptionsIn.classes().values()) {
			String clsNameIn = ci.name();
			String clsNameOut = mapper.mapClass(clsNameIn);

			ClassEntry co = new ClassEntry(clsNameOut);
			exceptionsOut.classes().put(co.name(), co);

			for (MethodEntry mi : ci.methods().values()) {
				String mtdNameIn = mi.name();
				String mtdDescIn = mi.descriptor();
				List<String> mtdExcsIn = mi.exceptions();
				String mtdNameOut = mapper.mapMethod(clsNameIn, mtdNameIn, mtdDescIn);
				String mtdDescOut = MappingUtils.translateMethodDescriptor(mtdDescIn, mapper);
				List<String> mtdExcsOut = new ArrayList<>(mtdExcsIn.size());
				for (String exc : mtdExcsIn) {
					mtdExcsOut.add(mapper.mapClass(exc));
				}

				MethodEntry mo = new MethodEntry(mtdNameOut, mtdDescOut, mtdExcsOut);
				co.methods().put(mo.name() + mo.descriptor(), mo);
			}
		}

		return this.exceptionsOut;
	}
}
